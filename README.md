layout: inline

# Run test
```
./gradlew run
```

## Sample annotated output
```txt annotate
-----Starting Pojo cache test-----
Basic cache get by key: Key:  h2, Age: 50

# 3 objects returns from SQL. We added 4 (.get) and removed 1 (.remove)
Cache-backing SQL store has all the objects: 3

# Performing 10k ScanQueries and showing start and end of GC counters -- 396 Young GCs, 18.6s, 3 rows returned, one with a very large name
Repeated ScanQuery start: [G1 Young Generation: count(8), time(43), G1 Old Generation: count(0), time(0)]
  Sample results count: 3
Repeated ScanQuery end took 18608 -- [G1 Young Generation: count(439), time(949), G1 Old Generation: count(0), time(0)]

# Performing 10k SqlQueries and showing start and end of GC counters -- 213 Young GCs, 7.3s, 3 rows returned, one with a very large name
Repeated SqlQuery with big object start: [G1 Young Generation: count(439), time(949), G1 Old Generation: count(0), time(0)]
  Sample results: 3
Repeated SqlQuery with big object end took 7386 -- [G1 Young Generation: count(652), time(1396), G1 Old Generation: count(0), time(0)]

# Performing 10k SqlQueries and showing start and end of GC counters -- 18 Young GCs, 2.8s, 1002 rows returned, no large names
Repeated SqlQuery without big object and 1000 entries: [G1 Young Generation: count(652), time(1396), G1 Old Generation: count(0), time(0)]
  Sample results: 1002
Repeated SqlQuery end took 2840 -- [G1 Young Generation: count(670), time(1441), G1 Old Generation: count(0), time(0)]

-----Starting Proto cache test-----
Basic cache get by key: Key:  h2, Age: 50
Binary cache get by key: Key: h1-binary, Age: 30

# Note the Class this is serialized to; an intermediate protobuf class. It's opaque to Ignite and requires serialization
Binary data field is serialized to: com.google.protobuf.GeneratedMessageLite$SerializedForm [idHash=753964406, hash=116228072, messageClass=class org.example.PersonProto$Person, messageClassName=org.example.PersonProto$Person, asBytes=[10, 9, 104, 49, 45, 98, 105, 110, 97, 114, 121, 16, 30]]

# Only the manually curated binary object is present
Cache-backing SQL store only has the binary objects: 1
BinaryConfiguration settings also don't work because `writeReplace` method transforms the object:
  writeBinary calls: 0
  readBinary calls: 0

# Performing 10k ScanQueries and showing start and end of GC counters -- 665 Young GCs, 24.7s, 4 rows returned, one with a very large name and 1 binary
Repeated ScanQuery start: [G1 Young Generation: count(670), time(1441), G1 Old Generation: count(0), time(0)]
  Sample results: 4
Repeated ScanQuery end took 24726 -- [G1 Young Generation: count(1335), time(2869), G1 Old Generation: count(0), time(0)]

# Performing 10k SqlQueries and showing start and end of GC counters -- 0 Young GCs, .18s, 1 row returned, the one small binary object
Repeated SqlQuery start: [G1 Young Generation: count(1335), time(2869), G1 Old Generation: count(0), time(0)]
  Sample results: 1
Repeated SqlQuery end took 183 -- [G1 Young Generation: count(1335), time(2869), G1 Old Generation: count(0), time(0)]

# Performing 10k SqlQueries and showing start and end of GC counters -- 20 Young GCs, 3s, 1001 row returned, no large names
Repeated SqlQuery without big object and 1000 binary entries: [G1 Young Generation: count(1335), time(2869), G1 Old Generation: count(0), time(0)]
  Sample results: 1001
Repeated SqlQuery end took 3005 -- [G1 Young Generation: count(1355), time(2912), G1 Old Generation: count(0), time(0)]
```

Some key takeaways:
* Protobuf objects won't allow the use of `QueryEntities` on the `CacheConfiguration` because of `writeReplace`
* `IgniteBinary` objects and SQL, even in the protobuf case, are the **fastest and most memory efficient** method of querying and peeking at objects
  * See https://ignite.apache.org/docs/latest/key-value-api/binary-objects