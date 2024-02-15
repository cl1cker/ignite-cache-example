layout: inline

# Run test
```
./gradlew run
```

## Sample annotated output
```bash annotate
-----Starting Pojo cache test-----
Basic cache get by key: Key:  h2, Age: 50

# 3 objects returns from SQL. We added 4 (.get) and removed 1 (.remove)
Cache-backing SQL store only has all the objects: 3 
# Performing 10k ScanQueries and showing start and end of GC counters
Repeated ScanQuery start: [G1 Young Generation: count(8), time(50), G1 Old Generation: count(0), time(0)]
  Sample results count: 3
Repeated ScanQuery end took 19288 -- [G1 Young Generation: count(648), time(1369), G1 Old Generation: count(0), time(0)]
# Performing 10k SqlQueries and showing start and end of GC counters
Repeated SqlQuery start: [G1 Young Generation: count(648), time(1369), G1 Old Generation: count(0), time(0)]
  Sample results: 3
Repeated SqlQuery end took 7554 -- [G1 Young Generation: count(925), time(1918), G1 Old Generation: count(0), time(0)]

-----Starting Proto cache test-----
Basic cache get by key: Key:  h2, Age: 50
Binary cache get by key: Key: h1-binary, Age: 30
# Note the Class this is serialized to; an intermediate protobuf class. It's opaque to Ignite and requires serialization
Binary data field is serialized to: com.google.protobuf.GeneratedMessageLite$SerializedForm [idHash=1015278026, hash=116228072, messageClass=class org.example.PersonProto$Person, messageClassName=org.example.PersonProto$Person, asBytes=[10, 9, 104, 49, 45, 98, 105, 110, 97, 114, 121, 16, 30]]
Cache-backing SQL store only has the binary objects: 1
BinaryConfiguration settings also don't work because `writeReplace` method transforms the object:
  writeBinary calls: 0
  readBinary calls: 0
Repeated ScanQuery start: [G1 Young Generation: count(925), time(1918), G1 Old Generation: count(0), time(0)]
  Sample results: 4
Repeated ScanQuery end took 24586 -- [G1 Young Generation: count(1694), time(3521), G1 Old Generation: count(0), time(0)]
Repeated SqlQuery start: [G1 Young Generation: count(1694), time(3521), G1 Old Generation: count(0), time(0)]
  Sample results: 1
Repeated SqlQuery end took 179 -- [G1 Young Generation: count(1694), time(3521), G1 Old Generation: count(0), time(0)]
```
