package io.micronaut.avro.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Micronaut Serde encoder backed by Avro binary encoder.
 */
public class AvroSerdeEncoder implements Encoder {

    private final org.apache.avro.io.Encoder delegate;
    private long count = 0;
    private boolean isArray = false;

    public AvroSerdeEncoder(org.apache.avro.io.Encoder delegate) {
        this.delegate = delegate;
    }

    public AvroSerdeEncoder(org.apache.avro.io.Encoder delegate, boolean isArray) {
        this.delegate = delegate;
        this.isArray = isArray;
    }

    private void startItem() throws IOException {
        delegate.startItem();
        count++;
    }
    public void setItemCount(long count) throws IOException {
        delegate.setItemCount(count);
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        delegate.writeArrayStart();
        return new AvroSerdeEncoder(delegate, true);
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        return new AvroSerdeEncoder(delegate, false);
    }

    @Override
    public void finishStructure() throws IOException {
        if (isArray) {
            delegate.writeArrayEnd();
        }
    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {
        // Avro records are schema-based: no need to encode keys
    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeString(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeBoolean(value);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeFixed(new byte[]{value});
    }

    @Override
    public void encodeShort(short value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeInt(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeInt(value);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeInt(value);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeLong(value);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeFloat(value);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeDouble(value);
    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeBytes(value.toByteArray());
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        if (isArray) {
            startItem();
        }
        //todo
        delegate.writeBytes(value.unscaledValue().toByteArray());
    }

    @Override
    public void encodeNull() throws IOException {
        if (isArray) {
            startItem();
        }
        delegate.writeNull();
    }

    public void encodeMapStart() throws IOException {

    }
    public void encodeMapEnd() throws IOException {
        delegate.writeMapEnd();
    }

    public void encodeEnum(int e) throws IOException {
        delegate.writeEnum(e);
    }


    public void flush() throws IOException {
        delegate.flush();
    }
}
