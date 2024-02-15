package org.example;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.QueryIndex;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataPageEvictionMode;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.NullLogger;

public class PersonProtoTest implements AutoCloseable {
    private static final String CACHE_TABLE_NAME = "TEST";
    private final AtomicInteger writeBinaryCounter = new AtomicInteger(0);
    private final AtomicInteger readBinaryCounter = new AtomicInteger(0);
    private final Ignite ignite;

    public PersonProtoTest() {
        var igniteConfiguration = new IgniteConfiguration()
                .setGridLogger(new NullLogger())
                .setClientMode(false)
                .setDataStorageConfiguration(new DataStorageConfiguration()
                        .setDefaultDataRegionConfiguration(new DataRegionConfiguration()
                                .setPageEvictionMode(DataPageEvictionMode.RANDOM_LRU)))
                // For example, this does't work either. By the time the type is checked to use
                // the custom serializer, our ptotobuf class is already a
                // GeneratedMessageLite$SerializedForm instance.
                .setBinaryConfiguration(new BinaryConfiguration()
                        .setTypeConfigurations(List.of(
                                new BinaryTypeConfiguration()
                                        .setTypeName(PersonProto.Person.class.getName())
                                        .setEnum(false)
                                        .setSerializer(new BinarySerializer() {
                                            @Override
                                            public void writeBinary(Object obj, BinaryWriter writer)
                                                    throws BinaryObjectException {
                                                writeBinaryCounter.incrementAndGet();
                                            }

                                            @Override
                                            public void readBinary(Object obj, BinaryReader reader)
                                                    throws BinaryObjectException {
                                                readBinaryCounter.incrementAndGet();
                                            }
                                        }))));
        ignite = Ignition.start(igniteConfiguration);
    }

    @Override
    public void close() throws Exception {
        ignite.close();
    }

    private List<List<?>> query(IgniteCache<String, PersonProto.Person> cache, String sql) {
        return cache.query(new SqlFieldsQuery(sql)).getAll();
    }

    public void validate(IgniteCache<String, PersonProto.Person> cache) {

    }

    public IgniteCache<String, PersonProto.Person> createProtobufCache() {
        var cacheConfig = new CacheConfiguration<String, PersonProto.Person>(CACHE_TABLE_NAME)
                .setStoreKeepBinary(true)
                .setQueryEntities(List.of(
                        new QueryEntity(String.class, PersonProto.Person.class)
                                .setTableName(CACHE_TABLE_NAME)
                                .addQueryField("age", Long.class.getName(), null)
                                .addQueryField("name", String.class.getName(), null)
                                .setIndexes(List.of(new QueryIndex("age")))));
        var cache = ignite.getOrCreateCache(cacheConfig);

        var personBuilder = PersonProto.Person.newBuilder();
        var person1 = personBuilder.setName("h1").setAge(30).build();
        var person1Binary = personBuilder.setName("h1-binary").setAge(30).build();
        var person2 = personBuilder.setName("h2").setAge(50).build();
        var person3 = personBuilder.setName("h3").setAge(60).build();
        var personBig = personBuilder.setName("name".repeat(1000000)).setAge(1000).build();
        cache.put(person1.getName(), person1);
        cache.put(person2.getName(), person2);
        cache.put(person3.getName(), person3);
        cache.put("big", personBig);
        cache.withKeepBinary().put(person1Binary.getName(), getAsBinary(person1Binary));
        cache.remove(person1.getName());
        return cache;
    }

    private BinaryObject getAsBinary(PersonProto.Person person) {
        return ignite.binary().builder(person.getClass().getName())
                .setField("age", person.getAge())
                .setField("name", person.getName())
                .setField("data", person)
                .build();
    }

    public void runTest(IgniteCache<String, PersonProto.Person> cache) {
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
        System.out.printf("Binary cache get by key: Key: %s, Age: %s%n", "h1-binary",
                ((BinaryObject) cache.withKeepBinary().get("h1-binary")).field("age"));
        System.out.println("Binary data field is serialized to: " +
                ((BinaryObject) cache.withKeepBinary().get("h1-binary")).field("data").toString());

        // query(cache, String.format("SELECT COUNT(*) FROM \"%s\".\"%s\"", name,
        // name));
        System.out.println("Cache-backing SQL store only has the binary objects: " +
                query(cache, String.format("SELECT name, age FROM \"%s\".\"%s\"", name, name)).size());
        // query(cache, String.format("EXPLAIN SELECT _key, * FROM \"%s\".\"%s\" WHERE
        // age < 100", name, name));

        System.out.println(
                "BinaryConfiguration settings also don't work because `writeReplace` method transforms the object:");
        System.out.printf("  writeBinary calls: %d%n", writeBinaryCounter.get());
        System.out.printf("  readBinary calls: %d%n", readBinaryCounter.get());

        // ScanQuery is not efficient
        var first = new AtomicBoolean(false);
        System.out.println("Repeated ScanQuery start: " + gc());
        var start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            var results = cache.query(new ScanQuery<String, PersonProto.Person>((k, v) -> v.getAge() < 10000)).getAll();
            if (first.compareAndSet(false, true)) {
                System.out.println("  Sample results: " + results.size());
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
        try (var test = new PersonProtoTest()) {
            test.runTest(test.createProtobufCache());
        }
    }

}
