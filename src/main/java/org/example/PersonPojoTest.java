package org.example;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.QueryIndex;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataPageEvictionMode;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.NullLogger;

public class PersonPojoTest implements AutoCloseable {
    private static final String CACHE_TABLE_NAME = "TEST";
    private final Ignite ignite;

    public PersonPojoTest() {
        var igniteConfiguration = new IgniteConfiguration()
                .setGridLogger(new NullLogger())
                .setClientMode(false)
                .setDataStorageConfiguration(new DataStorageConfiguration()
                        .setDefaultDataRegionConfiguration(new DataRegionConfiguration()
                                .setPageEvictionMode(DataPageEvictionMode.RANDOM_LRU)));
        ignite = Ignition.start(igniteConfiguration);
    }

    @Override
    public void close() throws Exception {
        ignite.close();
    }

    private List<List<?>> query(IgniteCache<String, PersonPojo> cache, String sql) {
        return cache.query(new SqlFieldsQuery(sql)).getAll();
    }

    public void validate(IgniteCache<String, PersonPojo> cache) {

    }

    public IgniteCache<String, PersonPojo> createPojoCache() {
        var cacheConfig = new CacheConfiguration<String, PersonPojo>(CACHE_TABLE_NAME)
                .setStoreKeepBinary(true)
                .setQueryEntities(List.of(
                        new QueryEntity(String.class, PersonPojo.class)
                                .setTableName(CACHE_TABLE_NAME)
                                .addQueryField("age", Long.class.getName(), null)
                                .addQueryField("name", String.class.getName(), null)
                                .setIndexes(List.of(new QueryIndex("age")))));
        var cache = ignite.getOrCreateCache(cacheConfig);

        var person1 = new PersonPojo("h1", 30);
        var person2 = new PersonPojo("h2", 50);
        var person3 = new PersonPojo("h3", 60);
        var personBig = new PersonPojo("name".repeat(1000000), 1000);
        cache.put(person1.getName(), person1);
        cache.put(person2.getName(), person2);
        cache.put(person3.getName(), person3);
        cache.put("big", personBig);
        cache.remove(person1.getName());
        return cache;
    }

    public void runTest(IgniteCache<String, PersonPojo> cache) {
        var name = cache.getName();
        // query(cache,
        // String.format(
        // "SELECT DISTINCT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
        // WHERE TABLE_SCHEMA = '%s' ORDER BY 1, 2",
        // name));
        // query(cache, String.format("SELECT * FROM SYS.TABLE_COLUMNS WHERE SCHEMA_NAME
        // = '%s'", name));
        // query(cache, String.format("SELECT * FROM SYS.INDEXES WHERE SCHEMA_NAME =
        // '%s'", name));

        System.out.printf("Basic cache get by key: Key:  %s, Age: %s%n", "h2", cache.get("h2").getAge());
        // query(cache, String.format("SELECT COUNT(*) FROM \"%s\".\"%s\"", name,
        // name));
        System.out.println("Cache-backing SQL store has all the objects: " +
                query(cache, String.format("SELECT name, age FROM \"%s\".\"%s\"", name, name)).size());
        // query(cache, String.format("EXPLAIN SELECT _key, * FROM \"%s\".\"%s\" WHERE
        // age < 100", name, name));

        var first = new AtomicBoolean(false);
        System.out.println("Repeated ScanQuery start: " + gc());
        var start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            var results = cache.query(new ScanQuery<String, PersonPojo>((k, v) -> v.getAge() < 10000)).getAll();
            if (first.compareAndSet(false, true)) {
                System.out.println("  Sample results count: " + results.size());
            }
        }
        System.out.println("Repeated ScanQuery end took " + (System.currentTimeMillis() - start) + " -- " + gc());

        // Note, using SQL, we can just get the fields we want
        first.set(false);
        System.out.println("Repeated SqlQuery start: " + gc());
        start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            var results = cache.query(new SqlFieldsQuery(String.format("SELECT age FROM \"%s\".\"%s\"", name, name)))
                    .getAll();
            if (first.compareAndSet(false, true)) {
                System.out.println("  Sample results: " + results.size());
            }
        }
        System.out.println("Repeated SqlQuery end took " + (System.currentTimeMillis() - start) + " -- " + gc());
    }

    public List<String> gc() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(b -> String.format("%s: count(%d), time(%d)", b.getName(), b.getCollectionCount(),
                        b.getCollectionTime()))
                .toList();
    }

    public static void run() throws Exception {
        try (var test = new PersonPojoTest()) {
            test.runTest(test.createPojoCache());
        }
    }

}
