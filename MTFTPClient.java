import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class MTFTPClient {

	private String serverAddress;
	private Integer nPort;
	private Integer tPort;

	private Socket socket = null;
	private static Scanner sysInput = null;
	private DataOutputStream serverOut = null;
	private DataInputStream serverIn = null;
	private static final String ACK = "ACK";

	public MTFTPClient(String address, Integer nPort, Integer tPort) {
		serverAddress = address;
		this.nPort = nPort;
		this.tPort = tPort;

		try {
			socket = new Socket(serverAddress, this.nPort);
			serverOut = new DataOutputStream(socket.getOutputStream());
			serverIn = new DataInputStream(socket.getInputStream());
		} catch (IOException e) {
			System.out.println("IO Exception occured while connecting");
			return;
		}
	}

	private void sendState(String state) {
		try {
			serverOut.writeUTF(state);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void startClient() {
		sysInput = new Scanner(System.in);
		String inputLine = "";
		while (true) {
			System.out.print("mytftp>");
			inputLine = sysInput.nextLine();

			if (inputLine.endsWith("&")) {
				String threadCommand = inputLine;
				new Thread(() -> {
					String add = serverAddress;
					Integer nnport = nPort;
					Integer ntport = tPort;
					String state = getpwd();
					MTFTPClient newclient = new MTFTPClient(add, nnport, ntport);
					newclient.sendState(state);
					newclient.processCommands(threadCommand);
					newclient.close();
					// System.out.print("mytftp>");
				}).start();
				continue;
			}
			processCommands(inputLine);
			if (inputLine.trim().equals("quit")) {
				break;
			}
		}
		return;
	}

	private void processCommands(String command) {

		String[] tokens = command.split("\\s+");

		if (tokens == null || tokens.length <= 0) {
			return;
		}

		if (tokens[0].equals("get") && tokens.length >= 2) {

			processGet(command);

		} else if (tokens[0].equals("put") && tokens.length >= 2) {

			processPut(command);

		} else if (tokens[0].equals("delete") && tokens.length >= 2) {

			processDel(command);

		} else if (tokens[0].equals("ls") && tokens.length >= 1) {

			processList(command);

		} else if (tokens[0].equals("cd") && tokens.length >= 2) {

			processChangeDir(command);

		} else if (tokens[0].equals("mkdir") && tokens.length >= 2) {

			processMakeDir(command);

		} else if (tokens[0].equals("pwd") && tokens.length >= 1) {

			processPwd(command);
		} else if (tokens[0].equals("quit") && tokens.length >= 1) {

			try {
				serverOut.writeUTF(command);
			} catch (IOException e) {
				System.out.println("error while quit");
			}
		} else if (tokens[0].equals("terminate") && tokens.length >= 2) {

			Socket tsocket;
			try {
				tsocket = new Socket(serverAddress, this.tPort);
				DataOutputStream tserverOut = new DataOutputStream(tsocket.getOutputStream());
				tserverOut.writeUTF(command);
				tserverOut.close();
				tsocket.close();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			System.out.println("Invalid command");
		}
		return;
	}

	private void processGet(String command) {
		String[] vals = command.split("\\s+");

		FileLock lock = null;
		try {
			serverOut.writeUTF(command);
			Long commandID = serverIn.readLong();
			System.out.println(commandID);
			if (!Thread.currentThread().getName().equalsIgnoreCase("main")) {
				System.out.print("mytftp>");
			}

			int bytes = 0;

			long size = serverIn.readLong();
			if (size <= 0) {
				System.out.println("No file found");
				return;
			}

			FileOutputStream fileOutputStream = new FileOutputStream(vals[1]);

			while (true) {
				try {
					lock = fileOutputStream.getChannel().lock();
					break;
				} catch (Exception e) {
					System.out.println("waiting...");
					TimeUnit.SECONDS.sleep(1);
				}
			}

			byte[] buffer = new byte[1000];
			while (size > 0 && serverIn.readUTF().equals("continue")
					&& (bytes = serverIn.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
				fileOutputStream.write(buffer, 0, bytes);
				size -= bytes;
			}
			if (size > 0) {
				File myObj = new File(System.getProperty("user.dir") + "/" + vals[1].trim());
				if (myObj.exists()) {
					myObj.delete();
				}
			}
			lock.release();
			fileOutputStream.close();

			return;
		} catch (Exception e) {
			if (lock != null) {
				try {
					lock.release();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			File myObj = new File(System.getProperty("user.dir") + "/" + vals[1].trim());
			if (myObj.exists()) {
				myObj.delete();
			}
			// e.printStackTrace();
			// System.out.println("Error while get");
		}

	}

	private void processPut(String command) {

		String[] vals = command.split("\\s+");
		File fl = new File(System.getProperty("user.dir"));
		List<String> files = Arrays.asList(fl.list());
		if (!files.contains(vals[1].trim())) {
			System.out.println("File not Found");
			return;
		}
		FileLock lock = null;
		try {
			serverOut.writeUTF(command);
			Long commandID = serverIn.readLong();
			System.out.println(commandID);
			if (!Thread.currentThread().getName().equalsIgnoreCase("main")) {
				System.out.print("mytftp>");
			}

			String filepath = System.getProperty("user.dir") + "/" + vals[1].trim();
			int bytes = 0;
			File file = new File(filepath);
			FileInputStream fileInputStream = new FileInputStream(file);

			while (true) {
				try {
					lock = fileInputStream.getChannel().lock(0, Long.MAX_VALUE, true);
					break;
				} catch (Exception e) {
					System.out.println("waiting...");
					TimeUnit.SECONDS.sleep(1);
				}
			}

			serverOut.writeLong(file.length());
			byte[] buffer = new byte[1000];
			while ((bytes = fileInputStream.read(buffer)) != -1) {
				serverOut.write(buffer, 0, bytes);
				serverOut.flush();
				if (serverIn.readUTF().equals("continue")) {
					continue;
				} else {
					// System.out.println("here");
					break;
				}
			}
			lock.release();
			fileInputStream.close();
			return;

		} catch (FileNotFoundException e) {
			System.out.println("File not Found");
		} catch (Exception e) {
			if (lock != null) {
				try {
					lock.release();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			System.out.println("Error while put");
		}

	}

	private String getpwd() {

		String msg = "";
		try {
			serverOut.writeUTF("workingdir");
			msg = serverIn.readUTF();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return msg;
	}

	private void process(String command) throws IOException {

		// System.out.println(command +" : "+ Thread.currentThread().getName());

		serverOut.writeUTF(command);
		while (true) {

			String msg = serverIn.readUTF();
			if (msg.equals(ACK)) {
				break;
			} else if (msg.contains("Error:")) {
				System.out.println(msg);
				break;
			}
			System.out.println(msg);
		}
		return;
	}

	private void processDel(String command) {

		try {
			process(command);
		} catch (IOException e) {
			System.out.println("Exception during delete");
		}
		return;
	}

	private void processList(String command) {

		try {
			process(command);
		} catch (IOException e) {
			System.out.println("Exception during ls");
			e.printStackTrace();
		}
		return;
	}

	private void processChangeDir(String command) {

		try {
			process(command);
		} catch (IOException e) {
			System.out.println("Exception during cd");
		}
		return;
	}

	private void processMakeDir(String command) {

		try {
			process(command);
		} catch (IOException e) {
			System.out.println("Exception during makedir");
		}
		return;

	}

	private void processPwd(String command) {

		try {
			process(command);

		} catch (IOException e) {
			System.out.println("Exception during pwd");
		}
		return;
	}

	private void close() {
		try {
			// sysInput.close();
			socket.close();
			serverIn.close();
			serverOut.close();
			// System.out.println("shutdown");
		} catch (IOException i) {
			System.out.println("IO Exception occured while closing");
		}
	}

	public static void main(String[] args) throws Exception {

		if (args == null || args.length != 3) {
			System.out.println("give server, nport, tport numbers as arguments");
			return;
		}
		MTFTPClient client = new MTFTPClient(args[0], Integer.valueOf(args[1]), Integer.valueOf(args[2]));
		client.sendState("");
		client.startClient();
		client.close();
	}

}
