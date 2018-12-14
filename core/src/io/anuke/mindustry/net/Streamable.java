package io.anuke.mindustry.net;

import java.nio.ByteBuffer;

public abstract class Streamable implements Packet{
    public byte[] bytes;

    @Override
    public boolean isImportant(){
        return true;
    }

    @Override
    public void write(ByteBuffer buffer){
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    @Override
    public void read(ByteBuffer buffer){
        int length = buffer.getInt();
        bytes = new byte[length];
        buffer.get(bytes);
    }
}
