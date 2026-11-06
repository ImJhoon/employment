import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;

/** Standalone synthetic measurement; does not start or modify the GRAB service. */
public final class CacheMemoryProbe {
    private static final int LIMIT = 100_000;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        boolean directExecutor = args.length > 0 && args[0].equals("direct");
        var builder = Caffeine.newBuilder().maximumSize(LIMIT).recordStats();
        if (directExecutor) {
            // Caffeine's own memory benchmark uses this to exclude the shared pool.
            builder.executor(Runnable::run);
        }
        Cache<UUID, Long> cache = builder.build();
        Random random = new Random(42L);
        for (int i = 0; i < LIMIT; i++) {
            // Synthetic distinct keys and boxed values outside the shared Long cache.
            cache.put(new UUID(random.nextLong(), random.nextLong()), 1_000L + i);
        }
        cache.cleanUp();
        if (!ForkJoinPool.commonPool().awaitQuiescence(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Maintenance did not quiesce");
        }
        cache.cleanUp();
        if (cache.estimatedSize() != LIMIT || cache.asMap().size() != LIMIT) {
            throw new IllegalStateException("The cache is not full: " + cache.estimatedSize());
        }

        var flags = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        System.out.println("JAVA_VERSION=" + System.getProperty("java.runtime.version"));
        System.out.println("JAVA_VENDOR=" + System.getProperty("java.vendor"));
        System.out.println("OS=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("PROCESSORS=" + Runtime.getRuntime().availableProcessors());
        System.out.println("EXECUTOR=" + (directExecutor ? "direct" : "project-default-common-pool"));
        System.out.println("ENTRIES=" + cache.estimatedSize());
        System.out.println("CACHE_CLASS=" + cache.getClass().getName());
        for (String name : new String[]{"MaxHeapSize", "UseG1GC", "UseCompressedOops",
                "UseCompressedClassPointers", "ObjectAlignmentInBytes", "ActiveProcessorCount"}) {
            System.out.println(name + "=" + flags.getVMOption(name).getValue());
        }
        System.out.println(VM.current().details());
        GraphLayout layout = GraphLayout.parseInstance(cache);
        System.out.println("REACHABLE_BYTES=" + layout.totalSize());
        System.out.printf("REACHABLE_MIB=%.6f%n", layout.totalSize() / 1048576.0);
        GraphLayout cacheGraph = directExecutor ? layout
                : layout.subtract(GraphLayout.parseInstance(ForkJoinPool.commonPool()));
        if (cacheGraph.getClassCounts().count(UUID.class) != LIMIT
                || cacheGraph.getClassCounts().count(Long.class) != LIMIT) {
            throw new IllegalStateException("The graph does not contain exactly 100000 keys and values");
        }
        System.out.println("CACHE_GRAPH_BYTES=" + cacheGraph.totalSize());
        System.out.printf("CACHE_GRAPH_MIB=%.6f%n", cacheGraph.totalSize() / 1048576.0);
        System.out.printf("PERCENT_OF_550_MIB=%.6f%n", cacheGraph.totalSize() * 100.0 / (550 * 1048576.0));
        System.out.println(cacheGraph.toFootprint());
        Reference.reachabilityFence(cache);
    }
}
