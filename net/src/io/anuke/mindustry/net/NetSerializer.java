package io.anuke.kryonet;

import io.anuke.mindustry.net.Packet;
import io.anuke.mindustry.net.Registrator;
import io.anuke.ucore.function.Supplier;
import io.anuke.ucore.util.Pooling;

import java.nio.ByteBuffer;

import static io.anuke.mindustry.net.Net.packetPoolLock;

@SuppressWarnings("unchecked")
public class NetSerializer {

    public static void write(ByteBuffer byteBuffer, Object o) {
        if (!(o instanceof Packet))
            throw new RuntimeException("All sent objects must implement be Packets! Class: " + o.getClass());
        byte id = Registrator.getID(o.getClass());
        if (id == -1)
            throw new RuntimeException("Unregistered class: " + o.getClass());
        byteBuffer.put(id);
        ((Packet) o).write(byteBuffer);
    }

    public static Object read(ByteBuffer byteBuffer) {
        byte id = byteBuffer.get();
        synchronized (packetPoolLock) {
            Packet packet = Pooling.obtain((Class<Packet>) Registrator.getByID(id).type, (Supplier<Packet>) Registrator.getByID(id).constructor);
            packet.read(byteBuffer);
            return packet;
        }
    }

    public static byte[] writeBytes(ByteBuffer buffer, Object object){
        synchronized(buffer){
            buffer.position(0);
            NetSerializer.write(buffer, object);
            int len = buffer.position();
            byte[] out = new byte[len];
            buffer.position(0);
            buffer.get(out);
            return out;
        }
    }

    public static Object readBytes(ByteBuffer buffer, byte[] data){
        synchronized(buffer){
            buffer.position(0);
            buffer.put(data);
            buffer.position(0);
            Object object = NetSerializer.read(buffer);
            return object;
        }
    }
}