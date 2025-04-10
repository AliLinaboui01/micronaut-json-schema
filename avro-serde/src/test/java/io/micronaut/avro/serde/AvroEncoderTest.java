package io.micronaut.avro.serde;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AvroEncoderTest {

    @Test
    void testEncodeStringValidInput() throws IOException {
        // Arrange
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AvroEncoder avroEncoder = new AvroEncoder(outputStream);

        // Act
        avroEncoder.writeString("foo");

        // Assert
        byte[] encodedBytes = outputStream.toByteArray();
        System.out.println(Arrays.toString(encodedBytes));
    }

    @Test
    void testEncodeStringEmptyString() throws IOException {
        // Arrange
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AvroEncoder avroEncoder = new AvroEncoder(outputStream);

        // Act
        avroEncoder.writeString("");

        // Assert
        byte[] encodedBytes = outputStream.toByteArray();
        System.out.println(Arrays.toString(encodedBytes));
        assertEquals("[0]", Arrays.toString(encodedBytes));
    }

    @Test
    void testEncodeIntValidInput() throws IOException {
        // Arrange
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AvroEncoder avroEncoder = new AvroEncoder(outputStream);

        // Act
        avroEncoder.writeInt(10);

        // Assert
        byte[] encodedBytes = outputStream.toByteArray();
        assertEquals("[20]", Arrays.toString(encodedBytes));
    }

    @Test
    void testSalamanderEncoder() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AvroEncoder encoder = new AvroEncoder(outputStream);

        encoder.writeInt(23);

        encoder.writeArrayStart();
        encoder.setItemCount(2);
        encoder.writeString("orange");
        encoder.writeString("blue");
        encoder.writeArrayEnd();

        encoder.writeMapStart();
        encoder.setItemCount(1);
        encoder.writeString("keyOne");
        encoder.writeString("val");
        encoder.writeMapEnd();

        encoder.writeString("foo");

        byte[] encodedBytes = outputStream.toByteArray();

        assertEquals("[46, 4, 12, 111, 114, 97, 110, 103, 101, 8, 98, 108, 117, 101, 0, 2, 12, 107, 101, 121, 79, 110, 101, 6, 118, 97, 108, 0, 6, 102, 111, 111]", Arrays.toString(encodedBytes));
    }

    @Test
    public void serializeArray() throws IOException {
          class Point {
            private final int x, y;

            private Point(int x, int y) {
                this.x = x;
                this.y = y;
            }

            public int[] coords() {
                return new int[] { x, y };
            }

            public Point valueOf(int x, int y) {
                return new Point(x, y);
            }
          }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // try-with-resource
        try (AvroSerdeEncoder encoder = new AvroSerdeEncoder(new AvroEncoder(out))){
            Argument<? extends Point> type = Argument.of(Point.class);
            Point value = new Point(3, 27);
            int[] coords = value.coords();
            encoder.setItemCount(coords.length);
            try (Encoder array = encoder.encodeArray(type)) {
                array.encodeInt(coords[0]);
                array.encodeInt(coords[1]);
            }

            // Flush to ensure all bytes are written
            encoder.flush();

            // Inspect raw bytes
            byte[] actualResult = out.toByteArray();
            Assertions.assertNotNull(actualResult);
            Assertions.assertTrue(actualResult.length > 0);
            String expectedBytes = "[4, 6, 54, 0]";  // (4) is the long length of the array  (6) is the binary vale of 3 (54) is the binary value of 27 and (0) indicate the end of array

            assertEquals(expectedBytes, Arrays.toString(actualResult));
        }
    }

    @Test
    public void serializeObject() throws IOException {
        class Male {

            private String name;
            private int lag;

            public Male() {
            }


            public Male(String name, int age) {
                this.name = name;
                this.lag = age;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public int getAge() {
                return lag;
            }

            public void setAge(int age) {
                this.lag = age;
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // try-with-resource
        try (AvroSerdeEncoder encoder = new AvroSerdeEncoder(new AvroEncoder(out))){
            Argument<? extends Male> type = Argument.of(Male.class);
            Male male = new Male("foo", 23);

            try (Encoder array = encoder.encodeObject(type)) {
                array.encodeString(male.getName());
                array.encodeInt(male.getAge());
            }

            // Flush to ensure all bytes are written
            encoder.flush();

            // Inspect raw bytes
            byte[] avroData = out.toByteArray();
            Assertions.assertNotNull(avroData);
            Assertions.assertTrue(avroData.length > 0);
            String actualResult = Arrays.toString(avroData);

            assertEquals("[6, 102, 111, 111, 46]", actualResult);

        }
    }

}
