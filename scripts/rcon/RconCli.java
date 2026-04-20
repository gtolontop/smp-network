import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class RconCli {
    private static final int TYPE_AUTH = 3;
    private static final int TYPE_EXEC = 2;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: RconCli <host> <port> <password> <command...>");
            System.exit(64);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String password = args[2];
        String command = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setSoTimeout(10000);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());

            send(out, 1, TYPE_AUTH, password);
            Packet auth = read(in);
            if (auth.id == -1) {
                System.err.println("[rcon] auth failed on " + host + ":" + port);
                System.exit(2);
            }

            send(out, 2, TYPE_EXEC, command);
            Packet resp = read(in);
            if (!resp.body.isEmpty()) System.out.print(resp.body);
        } catch (IOException e) {
            System.err.println("[rcon] " + host + ":" + port + " -> " + e.getMessage());
            System.exit(3);
        }
    }

    private static void send(DataOutputStream out, int id, int type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.US_ASCII);
        int length = 4 + 4 + payload.length + 2;
        ByteBuffer buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(payload);
        buf.put((byte) 0);
        buf.put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private static Packet read(DataInputStream in) throws IOException {
        byte[] header = new byte[4];
        in.readFully(header);
        int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] data = new byte[length];
        in.readFully(data);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int id = buf.getInt();
        int type = buf.getInt();
        byte[] body = new byte[length - 10];
        buf.get(body);
        return new Packet(id, type, new String(body, StandardCharsets.US_ASCII));
    }

    private record Packet(int id, int type, String body) {}
}
