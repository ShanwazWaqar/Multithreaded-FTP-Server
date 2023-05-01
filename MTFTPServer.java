import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MTFTPServer {

	public static final String ACK = "ACK";
	public static ArrayList<Long> toBeTerminated = new ArrayList<Long>();

	public static synchronized void addToTerminateList(Long id) {
		toBeTerminated.add(id);
	}

	public static synchronized void removeFromTerminateList(Long id) {
		toBeTerminated.remove(id);
	}

	public static synchronized boolean isPresentTerminateList(Long id) {
		return toBeTerminated.contains(id);
	}

	public static void main(String[] args) {
		if (args == null || args.length != 2) {
			System.out.println("give nport and tport numbers as arguments");
			return;
		}
		MTFTPServer mtftpServer = new MTFTPServer();
		NPortHandler nPortHandler = mtftpServer.new NPortHandler(Integer.valueOf(args[0]));
		nPortHandler.start();
		TPortHandler tPortHandler = mtftpServer.new TPortHandler(Integer.valueOf(args[1]));
		tPortHandler.start();

	}

	class NPortHandler extends Thread {

		public Integer nPort;

		NPortHandler(Integer port) {
			nPort = port;
		}

		public void run() {
			try {
				ServerSocket server = new ServerSocket(nPort);
				int counter = 0;
				System.out.println("Server nPort Started ....");
				while (true) {
					counter++;
					Socket serverClient = server.accept();
					System.out.println("Client No:" + counter + " starting!");
					NPortServer sct = new NPortServer(serverClient);
					sct.start(); // new thread for client
				}
			} catch (Exception e) {
				System.out.println(e);
			}
		}
	}

	class TPortHandler extends Thread {

		// handling tport functionalities

		public Integer tPort;

		TPortHandler(Integer port) {
			tPort = port;
		}

		public void run() {
			try {
				ServerSocket tserver = new ServerSocket(tPort);
				int counter = 0;
				System.out.println("Server tPort Started ....");
				while (true) {
					counter++;
					Socket serverTerminateClient = tserver.accept();
					System.out.println("client tport connection no " + counter + " success");
					DataInputStream in = new DataInputStream(
							new BufferedInputStream(serverTerminateClient.getInputStream()));
					String line = in.readUTF();
					if (line.contains("terminate")) {
						String[] vals = line.split("\\s+");
						Long commandID = Long.parseLong(vals[1].trim());
						addToTerminateList(commandID);
					}
					in.close();
					serverTerminateClient.close();
				}
			} catch (Exception e) {
				System.out.println(e);
			}
		}

		public void execTerminate(String command) {

		}
	}

	class NPortServer extends Thread {

		private Socket socket = null;
		private DataInputStream in = null;
		private DataOutputStream out = null;
		private String currentDir = "";

		NPortServer(Socket conSocket) {
			socket = conSocket;
		}

		public void run() {
			currentDir = System.getProperty("user.dir");
			try {

				System.out.println("nport server for client started");
				in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
				out = new DataOutputStream(socket.getOutputStream());
				String state = in.readUTF();
				if (state.isEmpty()) {
					System.out.println("here");
					currentDir = System.getProperty("user.dir");
				} else {
					System.out.println("and here");
					currentDir = state;
				}
				String line = "";

				while (true) {
					line = in.readUTF();
//					if(line.endsWith("&")) {
//						String commandLine=line;
//						new Thread(() -> {
//							this.processCommands(commandLine);
//						}).start();
//						continue;
//					}
					processCommands(line);
					if (line.equals("quit")) {
						break;
					}
				}

			} catch (IOException e) {

				close();

			}
			return;
		}

		private void close() {
			System.out.println("Closing connection");
			try {
				if (socket != null)
					socket.close();
				if (in != null)
					in.close();
				if (out != null)
					out.close();
			} catch (IOException e) {
				System.out.println("IO Exception occured while closing");
			}
		}

		private void processCommands(String command) {
			System.out.println(command + " : " + Thread.currentThread().getName());

			if (command.contains("get")) {

				processGet(command);

			} else if (command.contains("put")) {

				processPut(command);

			} else if (command.contains("delete")) {

				processDel(command);

			} else if (command.contains("ls")) {

				processList();

			} else if (command.contains("cd")) {

				processChangeDir(command);

			} else if (command.contains("mkdir")) {

				processMakeDir(command);

			} else if (command.contains("workingdir")) {

				processGetDir();

			} else if (command.contains("pwd")) {

				processPwd();
			} else if (command.contains("quit")) {
				close();
			}
			return;
		}

		private void processGet(String command) {
			Long threadid = Thread.currentThread().getId();
			try {
				out.writeLong(threadid);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			String[] vals = command.split("\\s+");
			String filepath = currentDir + "/" + vals[1].trim();
			int bytes = 0;

			File fl = new File(currentDir);
			List<String> files = Arrays.asList(fl.list());
			System.out.println(files);

			if (!files.contains(vals[1].trim())) {
				try {
					out.writeLong(0l);
				} catch (IOException e) {
					System.out.println("Error in get");
				}
				return;
			}

			File file = new File(filepath);
			System.out.println(filepath);
			FileInputStream fileInputStream;
			FileLock lock = null;
			try {
				fileInputStream = new FileInputStream(file);

				while (true) {
					try {
						lock = fileInputStream.getChannel().lock(0, Long.MAX_VALUE, true);
						break;
					} catch (Exception e) {
						System.out.println("waiting...");
						TimeUnit.SECONDS.sleep(1);
					}
				}

				out.writeLong(file.length());
				byte[] buffer = new byte[1000];
				while ((bytes = fileInputStream.read(buffer)) != -1) {

					if (isPresentTerminateList(threadid)) {
						System.out.println("terminating");
						removeFromTerminateList(threadid);
						out.writeUTF("terminate");
						break;
					}
					out.writeUTF("continue");
					out.write(buffer, 0, bytes);
					out.flush();
				}
				lock.release();
				fileInputStream.close();
				return;
			} catch (FileNotFoundException e) {
				System.out.println("File not found");
			} catch (Exception e) {
				if (lock != null) {
					try {
						lock.release();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
				e.printStackTrace();
				System.out.println("Error in get");
			}

		}

		private void processPut(String command) {
			Long threadid = Thread.currentThread().getId();
			try {
				out.writeLong(threadid);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			String[] vals = command.split("\\s+");
			String filepath = currentDir + "/" + vals[1].trim();
			FileLock lock = null;
			try {
				int bytes = 0;
				FileOutputStream fileOutputStream = new FileOutputStream(filepath);

				while (true) {
					try {
						lock = fileOutputStream.getChannel().lock();
						break;
					} catch (Exception e) {
						System.out.println("waiting...");
						TimeUnit.SECONDS.sleep(1);
					}
				}

				long size = in.readLong();
				byte[] buffer = new byte[1000];
				boolean isterminated = false;
				while (size > 0 && (bytes = in.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
					if (isPresentTerminateList(threadid)) {
						isterminated = true;
						removeFromTerminateList(threadid);
						out.writeUTF("terminate");
						break;
					}
					fileOutputStream.write(buffer, 0, bytes);
					size -= bytes;
					out.writeUTF("continue");
				}
				if (isterminated) {
					System.out.println("term here");
					File tempFile = new File(filepath);
					if (tempFile.exists()) {
						tempFile.delete();
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
						e1.printStackTrace();
					}
				}
				System.out.println("Error while put");
			}
		}

		private void processDel(String command) {
			String[] vals = command.split("\\s+");
			File fl = new File(currentDir + "/" + vals[1].trim());
			FileLock dellock = null;
			try {
				if (fl.exists()) {
					FileOutputStream fileOutputStream = new FileOutputStream(fl);
					while (true) {
						try {
							dellock = fileOutputStream.getChannel().lock();
							break;
						} catch (Exception e) {
							System.out.println("waiting...");
							TimeUnit.SECONDS.sleep(1);
						}
					}

					if (!fl.delete()) {
						System.out.println("file not deleted");
					}
					try {
						out.writeUTF(ACK);
					} catch (IOException e) {
						System.out.println("error while deleting");
					}
					dellock.release();
					fileOutputStream.close();
				} else {
					try {
						out.writeUTF("Error:File not found");
					} catch (Exception e) {
						System.out.println("no file found");
					}
				}
			} catch (Exception e) {
				if (dellock != null) {
					try {
						dellock.release();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
			return;

		}

		private void processList() {

			File fl = new File(currentDir);
			String[] files = fl.list();
			try {
				for (int i = 0; i < files.length; i++) {
					out.writeUTF(files[i]);
				}
				out.writeUTF(ACK);
			} catch (IOException e) {
				try {
					out.writeUTF("Error:processing in ls");
				} catch (IOException e1) {
					return;
				}
			}
			return;
		}

		private void processChangeDir(String command) {
			String[] vals = command.split("\\s+");

			// usecases: what if absolute path

			vals[1] = vals[1].trim();
			if (vals[1].length() != 1 && vals[1].endsWith("/")) {
				vals[1] = vals[1].substring(0, vals[1].length() - 1);
			}

			if (vals[1].trim().equalsIgnoreCase("~")) {

				currentDir = System.getProperty("user.dir");

			} else if (vals[1].trim().equalsIgnoreCase("..")) {

				if (!currentDir.equals("/")) {
					int ind = currentDir.lastIndexOf("/");
					currentDir = currentDir.substring(0, ind);
				}

			} else if (vals[1].trim().startsWith("../")) {

				if (!currentDir.equals("/")) {
					int ind = currentDir.lastIndexOf("/");
					currentDir = currentDir.substring(0, ind);
					String cmd = vals[0] + " " + vals[1].trim().substring(3);
					processChangeDir(cmd);
					return;
				}

			} else {

				String temp = vals[1].trim().startsWith("/") ? vals[1].trim() : currentDir + "/" + vals[1].trim();
				File tempPath = new File(temp);
				if (tempPath.isDirectory()) {
					currentDir = temp;
				} else {
					try {
						out.writeUTF("Error:No Directory Found");
						return;
					} catch (IOException e1) {
						return;
					}
				}
			}

			if (currentDir.length() != 1 && currentDir.endsWith("/")) {
				currentDir = currentDir.substring(0, currentDir.length() - 1);
			}

			try {
				out.writeUTF(ACK);
			} catch (IOException e) {
				System.out.println("IO Exception occured while changeDir");
			}

		}

		private void processMakeDir(String command) {
			String[] vals = command.split("\\s+");
			File fl = new File(currentDir);
			List<String> files = Arrays.asList(fl.list());
			try {
				if (files.contains(vals[1])) {
					out.writeUTF(ACK);
					return;
				}
				Path path = Paths.get(currentDir + "/" + vals[1]);
				Files.createDirectory(path);
				out.writeUTF(ACK);
				return;
			} catch (IOException e) {
				try {
					out.writeUTF("Error:processing mkdir");
				} catch (IOException e1) {
					return;
				}
				System.out.println("IO Exception occured while mkdir");
			}
		}

		private void processGetDir() {
			try {
				out.writeUTF(currentDir);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		private void processPwd() {
			try {
				out.writeUTF(currentDir);
				out.writeUTF(ACK);
			} catch (IOException e) {
				try {
					out.writeUTF("Error:processing mkdir");
				} catch (IOException e1) {
					return;
				}
			}
			return;
		}

	}

}
