package io.anuke.mindustry.net;

import com.badlogic.gdx.utils.Array;
import io.anuke.hexnet.HexConnection;
import io.anuke.hexnet.HexServer;
import io.anuke.hexnet.HexServerListener;
import io.anuke.hexnet.Mode;
import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.Net.ServerProvider;
import io.anuke.mindustry.net.Packets.StreamBegin;
import io.anuke.mindustry.net.Packets.StreamChunk;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerImpl implements ServerProvider{
    final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
    final CopyOnWriteArrayList<ConnectionImpl> connections = new CopyOnWriteArrayList<>();
    final Array<ConnectionImpl> array = new Array<>();
    final HexServer server = new HexServer();

    @Override
    public void host(int port) throws IOException{
        server.setSerializer(new SerializerImpl());
        server.setDiscovery(NetworkIO::writeServerData);
        server.setListener(new HexServerListener(){

        });

        server.open(port);
        connections.clear();
    }

    @Override
    public void sendStream(int id, Streamable stream){
        try{
            int cid;
            StreamBegin begin = new StreamBegin();
            begin.total = stream.stream.available();
            begin.type = Registrator.getID(stream.getClass());
            sendTo(id, begin, SendMode.tcp);
            cid = begin.id;

            while(stream.stream.available() > 0){
                byte[] bytes = new byte[Math.min(512, stream.stream.available())];
                stream.stream.read(bytes);

                StreamChunk chunk = new StreamChunk();
                chunk.id = cid;
                chunk.data = bytes;
                sendTo(id, chunk, SendMode.tcp);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void send(Object object, SendMode mode){
        for(ConnectionImpl c : connections){
           sendRaw(c, object, mode);
        }
    }

    @Override
    public void sendTo(int id, Object object, SendMode mode){
        sendRaw(getByID(id), object, mode);
    }

    @Override
    public void sendExcept(int id, Object object, SendMode mode){
        for(ConnectionImpl c : connections){
            if(c.id != id){
                sendRaw(c, object, mode);
            }
        }
    }

    void sendRaw(ConnectionImpl c, Object object, SendMode mode){
        server.send(c.hex, mode == SendMode.tcp ? Mode.reliable : Mode.unreliable, object);
    }

    @Override
    public void close(){
        server.close();
    }

    @Override
    public byte[] compressSnapshot(byte[] input){
        return compressor.compress(input);
    }

    @Override
    public Array<ConnectionImpl> getConnections(){
        array.clear();
        for(ConnectionImpl c : connections){
            array.add(c);
        }
        return array;
    }

    @Override
    public ConnectionImpl getByID(int id){
        for(ConnectionImpl n : connections){
            if(n.id == id){
                return n;
            }
        }
        return null;
    }

    @Override
    public void dispose(){
        close();
    }

    class ConnectionImpl extends NetConnection{
        final HexConnection hex;

        public ConnectionImpl(HexConnection con, String address){
            super(con.id, address);
            this.hex = con;
        }

        @Override
        public void close(){
            server.disconnect(hex);
        }
    }
}
