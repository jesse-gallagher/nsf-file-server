/**
 * Copyright (c) 2019-2024 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.nsffile.ssh;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.hcl.domino.DominoClient;
import com.hcl.domino.DominoClientBuilder;
import com.hcl.domino.DominoProcess;
import com.hcl.domino.server.ServerStatusLine;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.RejectAllPasswordAuthenticator;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.openntf.nsffile.core.config.DominoNSFConfiguration;
import org.openntf.nsffile.ssh.auth.NotesPasswordAuthenticator;
import org.openntf.nsffile.ssh.auth.NotesPublicKeyAuthenticator;

/**
 * Frontend-independent manager for running the SSH/SFTP server.
 * 
 * @author Jesse Gallagher
 * @since 1.0.0
 */
@SuppressWarnings("nls")
public class SshServerDelegate implements AutoCloseable {
	public static final Logger log = Logger.getLogger(SshServerDelegate.class.getPackage().getName());
	
	private final int port;
	private final KeyPairProvider keyPairProvider;
	private final FileSystemFactory fileSystemFactory;
	private final ScpCommandFactory scpCommandFactory;
	
	private SshServer server;
	private DominoClient dominoClient;
	private ServerStatusLine statusLine;

	public SshServerDelegate(int port, KeyPairProvider keyPairProvider, FileSystemFactory fileSystemFactory, ScpCommandFactory scpCommandFactory) {
		this.port = port;
		this.keyPairProvider = keyPairProvider;
		this.fileSystemFactory = fileSystemFactory;
		this.scpCommandFactory = scpCommandFactory;
	}
	
	public void start() throws IOException {
		if(log.isLoggable(Level.INFO)) {
			log.info(getClass().getSimpleName() + ": Startup");
			log.info(getClass().getSimpleName() + ": Using port " + port);
			log.info(getClass().getSimpleName() + ": Using key provider " + keyPairProvider);
		}
		
		server = ServerBuilder.builder()
			.fileSystemFactory(fileSystemFactory)
			.publickeyAuthenticator(new NotesPublicKeyAuthenticator())
			.build();
		
		server.setPort(port);
		server.setKeyPairProvider(keyPairProvider);
		
		if(DominoNSFConfiguration.instance.isAllowPasswordAuth()) {
			server.setPasswordAuthenticator(new NotesPasswordAuthenticator());
		} else {
			server.setPasswordAuthenticator(RejectAllPasswordAuthenticator.INSTANCE);
		}
		
		SftpSubsystemFactory sftp = new SftpSubsystemFactory.Builder()
			.build();
		server.setSubsystemFactories(Collections.singletonList(sftp));
		
		server.setCommandFactory(scpCommandFactory);
		
		server.start();
		
		DominoProcess.get().initializeThread();
		dominoClient = DominoClientBuilder.newDominoClient().build();
		statusLine = dominoClient.getServerAdmin().createServerStatusLine("SFTP Server");
		statusLine.setLine(MessageFormat.format("Listen for connect requests on TCP Port:{0}", Integer.toString(port)));
	}
	
	@Override
	public void close() throws IOException {
		DominoProcess.get().initializeThread();
		if(server != null) {
			server.close();
		}
		if(statusLine != null) {
			statusLine.close();
		}
		if(dominoClient != null) {
			dominoClient.close();
		}
	}
}
