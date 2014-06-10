package com.openxc.messages;

import java.util.HashMap;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import com.openxc.messages.UnrecognizedMessageTypeException;

import android.os.Parcel;

@Config(emulateSdk = 18, manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class VehicleMessageTest {
    VehicleMessage message;
    HashMap<String, Object> data;

    @Before
    public void setup() {
        data = new HashMap<String, Object>();
        data.put("value", Integer.valueOf(42));
        message = new VehicleMessage(data);
    }

    @Test
    public void getsATimestamp() {
        assertTrue(message.isTimestamped());
        assertTrue(message.getTimestamp() > 0);
    }

    @Test
    public void hasAValue() {
        assertEquals(42, message.get("value"));
    }

    @Test
    public void emptyMessage() {
        message = new VehicleMessage(new HashMap<String, Object>());
        assertTrue(message.getValuesMap() != null);
    }

    @Test
    public void timestampExtractedFromValues() {
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put("value", Integer.valueOf(42));
        data.put(VehicleMessage.TIMESTAMP_KEY, Long.valueOf(1000));
        message = new VehicleMessage(data);

        assertEquals(42, message.get("value"));
        assertFalse(message.contains(VehicleMessage.TIMESTAMP_KEY));
        assertEquals(null, message.get(VehicleMessage.TIMESTAMP_KEY));
        assertEquals(1000, message.getTimestamp());
    }

    @Test
    public void setManualTimestamp() {
        message = new VehicleMessage(Long.valueOf(10000), data);
        assertTrue(message.isTimestamped());
        assertEquals(10000, message.getTimestamp());
    }

    @Test
    public void untimestamp() {
        message = new VehicleMessage(Long.valueOf(10000), data);
        message.untimestamp();
        assertFalse(message.isTimestamped());
    }

    @Test
    public void sameEquals() {
        assertEquals(message, message);
    }

    @Test
    public void sameValuesEquals() {
        VehicleMessage anotherMessage = new VehicleMessage(
                message.getTimestamp(), data);
        assertEquals(message, anotherMessage);
    }

    @Test
    public void differentTimestampNotEqual() {
        VehicleMessage anotherMessage = new VehicleMessage(
                Long.valueOf(10000), data);
        assertFalse(message.equals(anotherMessage));
    }

    @Test
    public void differentValuesNotEqual() {
        data.put("another", "foo");
        // This also tests that the values map is copied and we don't have the
        // same reference from outside the class
        VehicleMessage anotherMessage = new VehicleMessage(
                message.getTimestamp(), data);
        assertFalse(message.equals(anotherMessage));
    }

    @Test
    public void writeAndReadFromParcel() {
        Parcel parcel = Parcel.obtain();
        message.writeToParcel(parcel, 0);

        // Reset parcel for reading
        parcel.setDataPosition(0);

        VehicleMessage createdFromParcel =
                VehicleMessage.CREATOR.createFromParcel(parcel);
        assertEquals(message, createdFromParcel);
    }

    @Test(expected=UnrecognizedMessageTypeException.class)
    public void buildFromEmptyValuesFails() throws UnrecognizedMessageTypeException {
        HashMap<String, Object> values = new HashMap<>();
        VehicleMessage.buildSubtype(values);
    }

    @Test(expected=UnrecognizedMessageTypeException.class)
    public void buildFromUnrecognizedFails() throws UnrecognizedMessageTypeException {
        HashMap<String, Object> values = new HashMap<>();
        values.put("foo", "bar");
        values.put("alice", Double.valueOf(42));
        VehicleMessage.buildSubtype(values);
    }

    @Test
    public void buildNamed() throws UnrecognizedMessageTypeException {
        HashMap<String, Object> values = new HashMap<>();
        values.put(NamedVehicleMessage.NAME_KEY, "bar");
        VehicleMessage message = VehicleMessage.buildSubtype(values);
        assertTrue(message instanceof NamedVehicleMessage);
        assertThat(((NamedVehicleMessage)message).getName(), equalTo("bar"));
    }
}