open module org.cloudfoundry.multiapps.controller.database.migration {

    exports org.cloudfoundry.multiapps.controller.database.migration;
    exports org.cloudfoundry.multiapps.controller.database.migration.client;
    exports org.cloudfoundry.multiapps.controller.database.migration.executor;
    exports org.cloudfoundry.multiapps.controller.database.migration.executor.type;
    exports org.cloudfoundry.multiapps.controller.database.migration.extractor;
    exports org.cloudfoundry.multiapps.controller.database.migration.generator;
    exports org.cloudfoundry.multiapps.controller.database.migration.metadata;

    requires transitive org.cloudfoundry.multiapps.controller.persistence;

    requires java.cfenv;
    requires java.naming;
    requires java.sql;
    requires log4j;
    requires org.cloudfoundry.multiapps.common;
    requires org.postgresql.jdbc;
    requires org.slf4j;

    requires static java.compiler;
    requires static org.immutables.value;

}