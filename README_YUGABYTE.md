# Cassandra Data Migrator with YugabyteDB Support

This is a fork of the [DataStax Cassandra Data Migrator](https://github.com/datastax/cassandra-data-migrator) with added support for migrating data from DataStax Cassandra to YugabyteDB YSQL (PostgreSQL-compatible).

## 🎯 **What's New**

This fork adds the following capabilities:

- **YugabyteDB YSQL Support**: Migrate data from Cassandra to YugabyteDB YSQL (PostgreSQL-compatible)
- **Automatic Data Type Mapping**: Converts Cassandra data types to PostgreSQL data types
- **Custom Migration Job**: `YugabyteMigrate` class for YugabyteDB-specific migrations
- **PostgreSQL JDBC Integration**: Uses PostgreSQL JDBC driver for YugabyteDB connections

## 🚀 **Quick Start**

### **1. Build the Project**
```bash
mvn clean package -DskipTests
```

### **2. Configure Migration**
Create a `yugabyte-migrate.properties` file:
```properties
# Origin (DataStax Cassandra)
spark.cdm.connect.origin.host=your-cassandra-host
spark.cdm.connect.origin.port=9042
spark.cdm.connect.origin.username=cassandra
spark.cdm.connect.origin.password=cassandra

# Target (YugabyteDB YSQL)
spark.cdm.connect.target.type=yugabyte
spark.cdm.connect.target.yugabyte.host=your-yugabyte-host
spark.cdm.connect.target.yugabyte.port=5433
spark.cdm.connect.target.yugabyte.database=yugabyte
spark.cdm.connect.target.yugabyte.username=yugabyte
spark.cdm.connect.target.yugabyte.password=yugabyte

# Table specification
spark.cdm.schema.origin.keyspaceTable=your_keyspace.customer
spark.cdm.schema.target.keyspaceTable=your_keyspace.customer
```

### **3. Run Migration**
```bash
spark-submit --properties-file yugabyte-migrate.properties \
--conf spark.cdm.schema.origin.keyspaceTable="your_keyspace.customer" \
--conf spark.cdm.trackRun=true \
--master "local[*]" --driver-memory 25G --executor-memory 25G \
--class com.datastax.cdm.job.YugabyteMigrate cassandra-data-migrator-5.5.2-SNAPSHOT.jar \
&> customer_migration_$(date +%Y%m%d_%H_%M).txt
```

## 📁 **New Files Added**

- `src/main/java/com/datastax/cdm/yugabyte/YugabyteSession.java` - YugabyteDB session management
- `src/main/java/com/datastax/cdm/schema/YugabyteTable.java` - YugabyteDB table schema handling
- `src/main/java/com/datastax/cdm/yugabyte/mapping/DataTypeMapper.java` - Data type mapping
- `src/main/java/com/datastax/cdm/yugabyte/statement/YugabyteUpsertStatement.java` - SQL upsert operations
- `src/main/java/com/datastax/cdm/job/YugabyteCopyJobSession.java` - Migration job session
- `src/main/java/com/datastax/cdm/job/YugabyteCopyJobSessionFactory.java` - Job session factory
- `src/main/scala/com/datastax/cdm/job/YugabyteMigrate.scala` - Main migration job
- `yugabyte-migrate.properties` - Example configuration
- `YUGABYTE_MIGRATION_README.md` - Detailed migration guide
- `JAR_MANAGEMENT_GUIDE.md` - JAR management guide
- `FINAL_MIGRATION_COMMAND.md` - Final command reference

## 🔧 **Modified Files**

- `pom.xml` - Added PostgreSQL JDBC driver dependency
- `src/main/java/com/datastax/cdm/properties/KnownProperties.java` - Added YugabyteDB properties
- `src/main/java/com/datastax/cdm/job/BaseJobSession.java` - Added YugabyteDB support

## 📊 **Data Type Mapping**

| Cassandra Type | PostgreSQL Type | Notes |
|----------------|-----------------|-------|
| `text` | `TEXT` | Direct mapping |
| `varchar` | `VARCHAR` | Direct mapping |
| `int` | `INTEGER` | Direct mapping |
| `bigint` | `BIGINT` | Direct mapping |
| `float` | `REAL` | Direct mapping |
| `double` | `DOUBLE PRECISION` | Direct mapping |
| `boolean` | `BOOLEAN` | Direct mapping |
| `timestamp` | `TIMESTAMP` | Direct mapping |
| `uuid` | `UUID` | Direct mapping |
| `blob` | `BYTEA` | Binary data |
| `list<text>` | `TEXT[]` | Array conversion |
| `set<text>` | `TEXT[]` | Array conversion |
| `map<text,text>` | `JSONB` | JSON conversion |

## 🎯 **Use Cases**

- **Database Migration**: Migrate from DataStax Cassandra to YugabyteDB
- **Data Consolidation**: Consolidate Cassandra data into YugabyteDB
- **Hybrid Cloud**: Move on-premises Cassandra to cloud YugabyteDB
- **Schema Evolution**: Transform Cassandra schema to PostgreSQL schema

## 📚 **Documentation**

- [YugabyteDB Migration Guide](YUGABYTE_MIGRATION_README.md)
- [JAR Management Guide](JAR_MANAGEMENT_GUIDE.md)
- [Final Migration Command](FINAL_MIGRATION_COMMAND.md)
- [Original CDM Documentation](https://github.com/datastax/cassandra-data-migrator)

## 🤝 **Contributing**

This is a fork of the original DataStax Cassandra Data Migrator. Contributions are welcome!

## 📄 **License**

This project is licensed under the Apache License 2.0 - see the [LICENSE.md](LICENSE.md) file for details.

## 🙏 **Acknowledgments**

- Original [DataStax Cassandra Data Migrator](https://github.com/datastax/cassandra-data-migrator) team
- [YugabyteDB](https://www.yugabyte.com/) for the PostgreSQL-compatible database
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/) for database connectivity
