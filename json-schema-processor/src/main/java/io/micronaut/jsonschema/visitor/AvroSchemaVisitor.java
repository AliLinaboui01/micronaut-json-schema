package io.micronaut.jsonschema.visitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.jsonschema.Avro;
import io.micronaut.jsonschema.model.AvroSchema;
import io.micronaut.jsonschema.model.AvroSchema.Type;
import io.micronaut.jsonschema.model.AvroSchema.LogicalType;
import io.micronaut.jsonschema.serialization.AvroSchemaMapperFactory;
import io.micronaut.jsonschema.visitor.context.AvroSchemaContext;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.stream.Collectors;

import static io.micronaut.jsonschema.visitor.context.AvroSchemaContext.AVRO_SCHEMA_CONTEXT_PROPERTY;


/**
 * A visitor for creating AVRO schemas for beans.
 * The bean must have a {@link Avro} annotation.
 *
 */
@Internal
public final class AvroSchemaVisitor implements TypeElementVisitor<Avro, Object> {

    private static final String SUFFIX = ".avsc";
    private static final String SLASH = "/";

    @Override
    public @NonNull TypeElementVisitor.VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return AvroSchemaContext.getParameters();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext visitorContext) {
        if (element.hasAnnotation(Avro.class)) {
            AvroSchemaContext context = visitorContext.get(AVRO_SCHEMA_CONTEXT_PROPERTY, AvroSchemaContext.class, null);
            if (context == null) {
                context = AvroSchemaContext.createDefault(visitorContext.getOptions());
                visitorContext.put(AVRO_SCHEMA_CONTEXT_PROPERTY, context);
            }
            context.currentOriginatingElements().clear();
            AvroSchema avroSchema = createTopLevelSchema(element, visitorContext, context);
            writeSchema(avroSchema, element, visitorContext, context);
        }
    }

    /**
     * A method for creating a AVRO schema. The schema will always a top-level schema and
     * never a reference.
     *
     * @param element The element
     * @param visitorContext The visitor context
     * @param context The Avro schema creation context
     * @return The schema
     */
    private static AvroSchema createTopLevelSchema(TypedElement element, VisitorContext visitorContext, AvroSchemaContext context) {
        ClassElement classElement = element.getGenericType();

        AvroSchema avroSchema = context.createdSchemasByType().get(classElement.getName());
        if (avroSchema != null) {
            context.currentOriginatingElements().add(classElement);
            return avroSchema;
        }
        avroSchema = new AvroSchema();

        AnnotationValue<Avro> schemaAnn = classElement.getAnnotation(Avro.class);
        if (schemaAnn != null) {
            avroSchema.setName(schemaAnn.stringValue("name")
                .orElse(classElement.getSimpleName()).replace('$', '.'));

            avroSchema.setNamespace(schemaAnn.stringValue("namespace")
                .orElse(classElement.getPackage().getName()));

            schemaAnn.stringValue("doc").ifPresent(avroSchema::setDoc);
            String[] aliases = schemaAnn.stringValues("aliases");
            if (aliases.length > 0) {
                avroSchema.setAliases(Arrays.asList(aliases));
            }
        }
        setSchemaType(element, visitorContext, context, avroSchema);

        if (schemaAnn != null) {
            context.createdSchemasByType().put(classElement.getName(), avroSchema);
        }
        return avroSchema;
    }

    /**
     * A method for creating a field of the schema.
     *
     * @param element The element
     * @param visitorContext The visitor context
     * @param context The JSON schema creation context
     * @return The schema
     */
    private static AvroSchema createSchema(TypedElement element, VisitorContext visitorContext, AvroSchemaContext context) {
        ClassElement classElement = element.getGenericType();

        // Handle simple types directly
        if (classElement.isAssignable(Number.class) || classElement.isPrimitive()) {
            AvroSchema primitiveSchema = new AvroSchema();
            setSchemaType(element, visitorContext, context, primitiveSchema);
            return primitiveSchema;
        }

        return createTopLevelSchema(element, visitorContext, context);
    }

    private static void setSchemaType(TypedElement element, VisitorContext visitorContext, AvroSchemaContext context, AvroSchema avroSchema) {
        ClassElement type = element.getGenericType();
        if ((type.getName().equals("byte") || type.getName().equals("java.lang.Byte")) && type.isArray()) {
            avroSchema.setType(Type.INT);
        }
        else if (type.isAssignable(Map.class)) {
            avroSchema.setType(Type.MAP);
            ClassElement valueType = type.getTypeArguments().get("V");
            AvroSchema valueSchema = createSchema(valueType, visitorContext, context);
            // Simplify primitive values in maps
            if (isPrimitiveType(valueType) || isPrimitiveAvroType(valueSchema.getType())) {
                avroSchema.setValues(valueSchema.getType().value().toLowerCase(Locale.ENGLISH));
            } else {
                avroSchema.setValues(valueSchema);
            }
        }else if(type.getName().equals("java.math.BigDecimal")){
            if (context.useDecimalLogicalType()) {
                avroSchema.setType(Type.STRING);
                avroSchema.setJava_class(type.getCanonicalName());
                avroSchema.setLogicalType(LogicalType.DECIMAL);
            } else {
                avroSchema.setType(Type.STRING);
            }
        }
        else if (isArrayType(type)) {
            avroSchema.setType(Type.ARRAY);
            avroSchema.setJava_class(type.getCanonicalName());
            ClassElement componentType = getArrayComponentType(type, visitorContext);
            AvroSchema itemSchema = createSchema(componentType, visitorContext, context);
            // If the component type is primitive, use the simple type string
            if (isPrimitiveType(componentType) || isPrimitiveAvroType(itemSchema.getType())) {
                avroSchema.setItems(itemSchema.getType().value().toLowerCase(Locale.ENGLISH));
            } else {
                avroSchema.setItems(itemSchema);
            }
        }
        else if (!type.isPrimitive() && type.getRawClassElement() instanceof EnumElement enumElement) {
            avroSchema.setType(Type.ENUM);
            for (String value : enumElement.values()) {
                avroSchema.addSymbol(value);
            }
            context.currentOriginatingElements().add(enumElement);
        }else if (isPrimitiveType(type)) {
                avroSchema.setType(getPrimitiveAvroType(type.getName()));
            }
        else if (type.isAssignable(Number.class)) {
            setNumericType(type, avroSchema, context);
        }
        else if (type.isAssignable(CharSequence.class) || type.getName().equals("char") || type.getName().equals("java.lang.Character")) {
            avroSchema.setType(Type.STRING);
        }
        else if (type.getName().equals("boolean") || type.getName().equals("java.lang.Boolean")) {
            avroSchema.setType(Type.BOOLEAN);
        }
        else if (type.isAssignable(Temporal.class) || type.isAssignable(TemporalAmount.class)) {
            setTemporalType(type, avroSchema, context);
        }
        else if (type.isAssignable(UUID.class)) {
            avroSchema.setType(Type.STRING);
            if (context.useLogicalTypes()) {
                avroSchema.setLogicalType(AvroSchema.LogicalType.UUID);
            }
        }
        else if (type.getName().equals("void") || type.getName().equals("java.lang.Void")) {
            avroSchema.setType(Type.NULL);
        }
        else if (!type.isPrimitive()) {
            setBeanSchemaFields(type, visitorContext, context, avroSchema);
        }
    }

    /**
     * Sets temporal type for the schema based on the Java type.
     *
     * @param type The class element representing the type
     * @param avroSchema The schema to configure
     * @param context The Avro schema creation context
     */
    private static void setTemporalType(ClassElement type, AvroSchema avroSchema, AvroSchemaContext context) {
        switch (type.getName()) {
            case "java.time.LocalDate" -> {
                avroSchema.setType(Type.INT);
                if (context.useLogicalTypes()) {
                    avroSchema.setLogicalType(AvroSchema.LogicalType.DATE);
                }
            }
            case "java.time.LocalTime" -> {
                avroSchema.setType(Type.INT);
                if (context.useTimeMillisLogicalType()) {
                    avroSchema.setLogicalType(LogicalType.TIME_MILLIS);
                }
            }
            case "java.time.LocalDateTime", "java.time.ZonedDateTime", "java.time.OffsetDateTime" -> {
                avroSchema.setType(Type.LONG);
                if (context.useTimestampMillisLogicalType()) {
                    avroSchema.setLogicalType(LogicalType.TIMESTAMP_MILLIS);
                }
            }
            case "java.time.Duration" -> {
                if (context.useLogicalTypes()) {
                    avroSchema.setType(Type.FIXED);
                    avroSchema.setSize(12); // Duration is 12 bytes
                    avroSchema.setLogicalType(LogicalType.DURATION);
                }else {
                    avroSchema.setType(Type.LONG);
                }
            }
            default -> avroSchema.setType(Type.STRING);
        }
    }

    /**
     * Sets numeric type for the schema based on the Java type.
     *
     * @param type The class element representing the type
     * @param avroSchema The schema to configure
     * @param context The Avro schema creation context
     */
    private static void setNumericType(ClassElement type, AvroSchema avroSchema, AvroSchemaContext context) {
        switch (type.getName()) {
            case "java.lang.Integer" -> avroSchema.setType(Type.INT);
            case "java.lang.Float" -> avroSchema.setType(Type.FLOAT);
            case "java.lang.Double" -> avroSchema.setType(Type.DOUBLE);
            case "java.lang.Long" -> avroSchema.setType(Type.LONG);
            case "java.lang.Short" -> avroSchema.setType(Type.INT); // Avro has no short type
            case "java.lang.Byte" -> avroSchema.setType(Type.INT); // Avro has no byte type
            case "java.math.BigInteger" -> {
                // BigInteger doesn't have a specific logical type
                avroSchema.setType(Type.STRING);
            }
            default -> avroSchema.setType(Type.DOUBLE);
        }
    }

    private static void setBeanSchemaFields(ClassElement element, VisitorContext visitorContext, AvroSchemaContext context, AvroSchema avroSchema) {
        avroSchema.setType(Type.RECORD);
        context.currentOriginatingElements().add(element);
        if (avroSchema.getName() == null) {
            avroSchema.setName(element.getSimpleName().replace('$', '.'));
        }
        context.createdSchemasByType().put(element.getName(), avroSchema);

        for (PropertyElement property : element.getBeanProperties()) {
            AvroSchema.Field field = new AvroSchema.Field();
            field.setName(property.getName());

            if (property.hasAnnotation(Avro.class)) {
                AnnotationValue<Avro> fieldSchema = property.getAnnotation(Avro.class);
                String[] aliases = fieldSchema.stringValues("aliases");
                if (aliases.length > 0) {
                    field.setAliases(Arrays.asList(aliases));
                }
                fieldSchema.stringValue("doc").ifPresent(field::setDoc);
            }

            // Create schema for the property
            AvroSchema propertySchema = createSchema(property, visitorContext, context);
            if (isPrimitiveType(property.getType()) || isPrimitiveAvroType(propertySchema.getType()) && !isLogicalAvroType(propertySchema.getLogicalType())) {
                field.setType(propertySchema.getType().value().toLowerCase(Locale.ENGLISH));
            } else {
                propertySchema.setName(property.getName());
                field.setType(propertySchema);
            }

            avroSchema.addField(field);
        }
    }

    private static void writeSchema(AvroSchema avroSchema, ClassElement originatingElement, VisitorContext visitorContext, AvroSchemaContext context) {
        String fileName = getFileName(avroSchema);
        String path = context.outputLocation() + SLASH + fileName;
        GeneratedFile specFile = visitorContext.visitMetaInfFile(path, originatingElement).orElse(null);
        if (specFile == null) {
            visitorContext.warn("Unable to get [\" " + path + "\"] file to write Avro schema", null);
        } else {
            visitorContext.info("Generating Avro schema file: " + specFile.getName());
            try (Writer writer = specFile.openWriter()) {
                ObjectMapper mapper = AvroSchemaMapperFactory.createMapper();
                List<AvroSchema.Field> sortedFields = avroSchema.getFields().stream()
                    .sorted(Comparator.comparing(AvroSchema.Field::getName)) // alphabetical, or custom
                    .collect(Collectors.toList());
                avroSchema.setFields(sortedFields);
                mapper.writeValue(writer, avroSchema);
            } catch (IOException e) {
                throw new RuntimeException("Failed writing Avro schema " + specFile.getName() + " file: " + e, e);
            }
        }
    }

    private static String getFileName(AvroSchema avroSchema) {
        String fileName;
        if(avroSchema.getFullName() != null) {
            fileName = avroSchema.getFullName();
        }else {
            fileName = avroSchema.getName();
        }
        fileName = fileName.replace('.', '/');
        return fileName + SUFFIX;
    }

    private static boolean isLogicalAvroType(LogicalType type) {
        return (type == LogicalType.DATE || type == LogicalType.UUID || type == LogicalType.DURATION ||
            type == LogicalType.DECIMAL || type == LogicalType.TIME_MILLIS || type == LogicalType.TIME_MICROS ||
            type == LogicalType.TIMESTAMP_MILLIS || type == LogicalType.TIMESTAMP_MICROS);
    }

    private static Type getPrimitiveAvroType(String typeName) {
        return switch (typeName) {
            case "int" -> Type.INT;
            case "float" -> Type.FLOAT;
            case "long" -> Type.LONG;
            case "double" -> Type.DOUBLE;
            case "short" -> Type.INT; // Avro has no short type
            case "byte" -> Type.INT; // Avro has no byte type
            case "boolean" -> Type.BOOLEAN;
            case "char" -> Type.STRING; // Characters are treated as strings in Avro
            case "void" -> Type.NULL;
            default ->
                throw new IllegalArgumentException("Unsupported primitive type: " + typeName);
        };
    }

    private static boolean isPrimitiveType(ClassElement type) {
        String typeName = type.getName();
        return typeName.equals("int") || typeName.equals("float") || typeName.equals("double") ||
            typeName.equals("long") || typeName.equals("short") || typeName.equals("byte") ||
            typeName.equals("boolean") || typeName.equals("char") || typeName.equals("void");
    }

    // Helper method to check if an Avro Type is primitive
    private static boolean isPrimitiveAvroType(Type type) {
        return (type == Type.INT || type == Type.LONG || type == Type.FLOAT ||
            type == Type.DOUBLE || type == Type.BOOLEAN || type == Type.STRING ||
            type == Type.BYTES || type == Type.NULL);
    }

    private static boolean isArrayType(ClassElement type) {
        return type.getName().endsWith("[]") || type.isAssignable(Collection.class);
    }

    private static ClassElement getArrayComponentType(ClassElement type, VisitorContext visitorContext) {
        if (type.getName().endsWith("[]")) {
            // Handle Java array types
            String arrayTypeName = type.getName();
            String componentTypeName = arrayTypeName.substring(0, arrayTypeName.length() - 2);

            // Get the ClassElement for the component type from the visitor context
            return visitorContext.getClassElement(componentTypeName)
                .orElseThrow(() -> new IllegalArgumentException("Cannot resolve array component type: " + componentTypeName));
        } else if (type.isAssignable(Collection.class)) {
            // Handle collections
            return type.getTypeArguments().get("E");
        }
        throw new IllegalArgumentException("Not an array type: " + type.getName());
    }
}
