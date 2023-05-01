Multi Threaded Client and Server



#### special compilation or execution instruction


	# How to run
	
	## Run Server
	
	Open The Terminal in the Src directory and enter the below commands:

	```
	$ javac MTFTPServer.java 

	$ java MTFTPServer 'nport-number' 'tport-number'

	```
	The server opens socket on nport and tport and is ready to accept multiple clients connections.

	### Run Client 
	
	Open another Terminal in the same/different system in the same src folder and enter the below commands:

	```
	
	$ javac MTFTPClient.java

	$ java MTFTPClient 'IP-address' 'nport-number' 'tport-number'

	```

	The client will be connected and ready to execute Simple FTP Commands.

##### Implemented Functionalities

1. get - Copy the file with the name <remote_filename> from the remote directory to the local directory.

	* get <remote_filename>

2. put - Copy file with the name <local_filename> from local directory to remote directory.

	* put <local_filename>

3. get & - Copy the file with the name <remote_filename> from the remote directory to the local directory in a separate thread. This command also provides command-id to be used by the terminate command.

	* get <remote_filename> &

4. put & - Copy the file with the name <local_filename> from the local directory to the remote directory in a separate thread. This command also provides command-id to be used by the terminate command.

	* put <remote_filename> &

5. delete – Delete the file with the name <remote_filename> from the remote directory.

	* delete <remote_filename>

6. ls - List the files and subdirectories in the remote directory.

	* ls

7. cd – Change to the <remote_direcotry_name > on the remote machine or change to the parent directory of the current directory

	* cd <remote_direcotry_name> or cd ..

8. mkdir – Create a directory named <remote_direcotry_name> as the sub-directory of the current working directory on the remote machine.

	* mkdir <remote_directory_name>

9. pwd – Print the current working directory on the remote machine.

	* pwd

10. terminate - the command identified by 'command-id', terminates the long running thread corresponding to the id

	* terminate <command-ID>

11. quit – End the FTP session.

	* quit












