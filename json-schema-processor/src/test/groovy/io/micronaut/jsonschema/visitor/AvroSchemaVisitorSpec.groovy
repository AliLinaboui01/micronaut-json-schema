package io.micronaut.jsonschema.visitor

import io.micronaut.jsonschema.model.AvroSchema

class AvroSchemaVisitorSpec extends AbstractAvroSchemaSpec {

    void "simple record schema"() {
        given:
        def avroSchema = buildAvroSchema('test.me.models.Salamander', 'Salamander', """
        package test.me.models;

        import io.micronaut.jsonschema.Avro;
        import java.util.*;
        import java.math.BigDecimal;

        @Avro
        public record Salamander(
            String name,
            int age,
            Color color,
            List<List<String>> environments,
            BigDecimal bd,
            Map<String, Map<String, String>> doubleMap
        ) {
            enum Color {
                RED,
                GREEN,
                BLUE
            }

}
""")
        expect:
        avroSchema.name == "Salamander"
        avroSchema.fields.get(1).name == "bd"
        avroSchema.fields.get(1).type.type == "string"
        avroSchema.fields.get(0).name == "age"
        avroSchema.fields.get(0).type == AvroSchema.Type.INT.value()
        avroSchema.fields.get(2).type.type == AvroSchema.Type.ENUM.value()
        avroSchema.fields.get(2).type.symbols == ["RED", "GREEN", "BLUE"]
        avroSchema.fields.get(2).name == "color"

    }

    void "simple record schema metadata"() {
        given:
        def avroSchema = buildAvroSchema('test.Salamander', 'salamander', """
        package test;

        import io.micronaut.jsonschema.Avro;
        import java.time.LocalDate;import java.time.LocalTime;import java.util.*;

        @Avro(
                name = "salamander",
                doc = "this a salamander record"
        )
        public record Salamander(
            String name,
                int age,
                LocalDate date,
                LocalTime time
        ) {


}
""")
        expect:
        avroSchema.name == "salamander"
        avroSchema.doc == "this a salamander record"
        avroSchema.fields.get(0).name == "name"
        avroSchema.fields.get(0).type == AvroSchema.Type.STRING.value()
        avroSchema.fields.get(1).name == "age"
        avroSchema.fields.get(1).type == AvroSchema.Type.INT.value()
        avroSchema.fields.get(2).name == "date"
        avroSchema.fields.get(2).type.type == AvroSchema.Type.INT.value()
        avroSchema.fields.get(2).type.logicalType == AvroSchema.LogicalType.DATE.name()
        avroSchema.fields.get(3).name == "time"
        avroSchema.fields.get(3).type.type == AvroSchema.Type.INT.value()
        avroSchema.fields.get(3).type.logicalType == AvroSchema.LogicalType.TIME_MILLIS.name()
    }

    void "simple record schema with nested list"() {
        given:
        def avroSchema = buildAvroSchema('test.Salamander', 'salamander', """
        package test;

        import io.micronaut.jsonschema.Avro;
        import java.time.LocalDate;
        import java.util.*;
        @Avro(
                name = "salamander",
                doc = "this a salamander class",
                aliases = {"test1, test2"}
        )
        public record Salamander(
            String name,
            boolean isTrue,
            Color color,
            List<List<String>> nestedList,
            String[][] array2d
        ) {
            enum Color {
            RED,
            GREEN,
            BLUE
        }

}
""")
        expect:
        avroSchema.name == "salamander"
        avroSchema.fields.get(0).name == "name"
        avroSchema.fields.get(0).type.type == AvroSchema.Type.STRING
        avroSchema.fields.get(1).name == "isTrue"
        avroSchema.fields.get(1).type.type == AvroSchema.Type.BOOLEAN
        avroSchema.fields.get(2).name == "color"
        avroSchema.fields.get(2).type.symbols == ["RED" ,"GREEN", "BLUE"]
        avroSchema.fields.get(3).name == "nestedList"
        avroSchema.fields.get(3).type.type == AvroSchema.Type.ARRAY
        avroSchema.fields.get(3).type.items.type == AvroSchema.Type.ARRAY
        avroSchema.fields.get(3).type.items.items.type == AvroSchema.Type.STRING
    }
    void "simple"() {
        given:
        def avroSchema = buildAvroSchema('test.Salamander', 'Salamander', """
        package test;

        import io.micronaut.jsonschema.Avro;
        import java.time.LocalDate;
        import java.util.*;
        @Avro
        public class Salamander{
            String name;
            boolean isTrue;
            public String getName() {
                return name;
            }

        }
""")
        expect:
        avroSchema.name == "Salamander"
    }
}
