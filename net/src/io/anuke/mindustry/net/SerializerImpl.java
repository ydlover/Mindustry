package io.anuke.mindustry.net;

import io.anuke.hexnet.HexSerializer;
import io.anuke.ucore.function.Supplier;
import io.anuke.ucore.util.Pooling;

import java.nio.ByteBuffer;

import static io.anuke.mindustry.net.Net.packetPoolLock;

@SuppressWarnings("unchecked")
public class SerializerImpl implements HexSerializer{

    @Override
    public void write(Object o, ByteBuffer byteBuffer) {
        if (!(o instanceof Packet))
            throw new RuntimeException("All sent objects must implement be Packets! Class: " + o.getClass());
        byte id = Registrator.getID(o.getClass());
        if (id == -1)
            throw new RuntimeException("Unregistered class: " + o.getClass());
        byteBuffer.put(id);
        ((Packet) o).write(byteBuffer);
    }

    @Override
    public Object read(ByteBuffer byteBuffer) {
        byte id = byteBuffer.get();
        synchronized (packetPoolLock) {
            Packet packet = Pooling.obtain((Class<Packet>) Registrator.getByID(id).type, (Supplier<Packet>) Registrator.getByID(id).constructor);
            packet.read(byteBuffer);
            return packet;
        }
    }
}