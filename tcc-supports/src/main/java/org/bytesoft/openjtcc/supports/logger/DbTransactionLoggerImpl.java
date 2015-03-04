/**
 * Copyright 2014 yangming.liu<liuyangming@gmail.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.openjtcc.supports.logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.bytesoft.openjtcc.Compensable;
import org.bytesoft.openjtcc.archive.CompensableArchive;
import org.bytesoft.openjtcc.archive.TerminatorArchive;
import org.bytesoft.openjtcc.archive.TransactionArchive;
import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.common.TransactionStatus;
import org.bytesoft.openjtcc.remote.RemoteTerminator;
import org.bytesoft.openjtcc.supports.TransactionLogger;
import org.bytesoft.openjtcc.supports.serialize.CompensableInfo;
import org.bytesoft.openjtcc.supports.serialize.CompensableMarshaller;
import org.bytesoft.openjtcc.supports.serialize.ObjectSerializer;
import org.bytesoft.openjtcc.supports.serialize.TerminatorInfo;
import org.bytesoft.openjtcc.supports.serialize.TerminatorMarshaller;
import org.bytesoft.openjtcc.xa.XidFactory;
import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.ByteUtils;
import org.springframework.jdbc.core.support.JdbcDaoSupport;

/**
 * 不推荐，应优先使用FileTransactionLoggerImpl。
 */
public class DbTransactionLoggerImpl extends JdbcDaoSupport implements TransactionLogger {
	private CompensableMarshaller compensableMarshaller;
	private ObjectSerializer serializer;
	private TerminatorMarshaller terminatorMarshaller;
	private TerminalKey instanceKey;
	private XidFactory xidFactory;

	@Override
	public void beginTransaction(TransactionArchive transaction) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("insert into tcc_transaction(");
			ber.append("application, endpoint, global_tx_id, status");
			ber.append(", status_trace, coordinator, created_time, deleted");
			ber.append(") values (?, ?, ?, ?, ?, ?, ?, ?)");
			stmt = connection.prepareStatement(ber.toString());

			TransactionContext transactionContext = transaction.getTransactionContext();
			TransactionStatus transactionStatus = transaction.getTransactionStatus();
			XidImpl globalXid = transactionContext.getGlobalXid();
			boolean coordinator = transactionContext.isCoordinator();
			TerminalKey terminalKey = transactionContext.getTerminalKey();

			int index = 1;
			stmt.setString(index++, terminalKey.getApplication());
			stmt.setString(index++, terminalKey.getEndpoint());
			stmt.setString(index++, ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()));
			stmt.setInt(index++, transactionStatus.getInnerStatus());
			stmt.setInt(index++, transactionStatus.getInnerStatusTrace());
			stmt.setBoolean(index++, coordinator);
			stmt.setTimestamp(index++, new Timestamp(transactionContext.getCreatedTime()));
			stmt.setBoolean(index++, false);

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public void enlistService(TransactionContext transactionContext, CompensableArchive holder) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("insert into tcc_compensable(");
			ber.append("application, endpoint, global_tx_id, branch_qualifier");
			ber.append(", coordinator, bean_name) values (?, ?, ?, ?, ?, ?)");
			stmt = connection.prepareStatement(ber.toString());

			Compensable<Serializable> service = holder.service;
			CompensableInfo servInfo = this.compensableMarshaller.marshallCompensable(service);
			XidImpl internalXid = holder.branchXid;
			TerminalKey terminalKey = transactionContext.getTerminalKey();

			stmt.setString(1, terminalKey.getApplication());
			stmt.setString(2, terminalKey.getEndpoint());
			stmt.setString(3, ByteUtils.byteArrayToString(internalXid.getGlobalTransactionId()));
			if (transactionContext.isCoordinator()) {
				stmt.setString(4, TransactionLogger.NULL);
			} else {
				stmt.setString(4, ByteUtils.byteArrayToString(internalXid.getBranchQualifier()));
			}
			stmt.setBoolean(5, holder.launchSvc);
			stmt.setString(6, String.valueOf(servInfo.getIdentifier()));

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public void delistService(TransactionContext transactionContext, CompensableArchive holder) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("update tcc_compensable set variable = ?, try_committed = ? ");
			ber.append("where application = ? and endpoint = ? and global_tx_id = ? and branch_qualifier = ?");
			stmt = connection.prepareStatement(ber.toString());

			XidImpl internalXid = holder.branchXid;

			Blob blob = connection.createBlob();
			OutputStream output = blob.setBinaryStream(1);
			Serializable variable = holder.variable;
			if (variable != null) {
				byte[] bytes = this.serializer.serialize(variable);
				output.write(bytes);
			}
			output.close();

			stmt.setBlob(1, blob);
			stmt.setBoolean(2, holder.tryCommitted);

			stmt.setString(3, this.instanceKey.getApplication());
			stmt.setString(4, this.instanceKey.getEndpoint());

			stmt.setString(5, ByteUtils.byteArrayToString(internalXid.getGlobalTransactionId()));
			if (transactionContext.isCoordinator()) {
				stmt.setString(6, TransactionLogger.NULL);
			} else {
				stmt.setString(6, ByteUtils.byteArrayToString(internalXid.getBranchQualifier()));
			}

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public void updateService(TransactionContext transactionContext, CompensableArchive holder) {
		this.updateServiceTransaction(transactionContext, holder);
	}

	@Override
	public void confirmService(TransactionContext transactionContext, CompensableArchive holder) {
		this.updateServiceTransaction(transactionContext, holder);
	}

	@Override
	public void cancelService(TransactionContext transactionContext, CompensableArchive holder) {
		this.updateServiceTransaction(transactionContext, holder);
	}

	@Override
	public void commitService(TransactionContext transactionContext, CompensableArchive holder) {
		this.updateServiceTransaction(transactionContext, holder);
	}

	@Override
	public void rollbackService(TransactionContext transactionContext, CompensableArchive holder) {
		this.updateServiceTransaction(transactionContext, holder);
	}

	@Override
	public void registerTerminator(TransactionContext transactionContext, TerminatorArchive holder) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("insert into tcc_terminator(");
			ber.append("application, endpoint, global_tx_id");
			ber.append(", to_application, to_endpoint");
			ber.append(") values (?, ?, ?, ?, ?)");
			stmt = connection.prepareStatement(ber.toString());

			RemoteTerminator terminator = holder.terminator;
			TerminalKey terminalKey = terminator.getTerminalKey();
			XidImpl branchXid = transactionContext.getGlobalXid();
			String globalTransactionId = ByteUtils.byteArrayToString(branchXid.getGlobalTransactionId());
			stmt.setString(1, this.instanceKey.getApplication());
			stmt.setString(2, this.instanceKey.getEndpoint());
			stmt.setString(3, globalTransactionId);
			stmt.setString(4, terminalKey.getApplication());
			stmt.setString(5, terminalKey.getEndpoint());

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public void prepareTerminator(TransactionContext transactionContext, TerminatorArchive holder) {
		this.updateTerminatorTransaction(transactionContext, holder);
	}

	@Override
	public void commitTerminator(TransactionContext transactionContext, TerminatorArchive holder) {
		this.updateTerminatorTransaction(transactionContext, holder);
	}

	@Override
	public void rollbackTerminator(TransactionContext transactionContext, TerminatorArchive holder) {
		this.updateTerminatorTransaction(transactionContext, holder);
	}

	@Override
	public void cleanupTerminator(TransactionContext transactionContext, TerminatorArchive holder) {
		this.updateTerminatorTransaction(transactionContext, holder);
	}

	@Override
	public void prepareTransaction(TransactionArchive transaction) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("update tcc_transaction set status = ?, status_trace = ? ");
			ber.append("where application = ? and endpoint = ? and global_tx_id = ?");// and branch_qualifier = ?
			stmt = connection.prepareStatement(ber.toString());

			TransactionContext transactionContext = transaction.getTransactionContext();
			TransactionStatus transactionStatus = transaction.getTransactionStatus();
			XidImpl globalXid = transactionContext.getGlobalXid();
			// boolean coordinator = transactionContext.isCoordinator();

			stmt.setInt(1, transactionStatus.getInnerStatus());
			stmt.setInt(2, transactionStatus.getInnerStatusTrace());
			stmt.setString(3, this.instanceKey.getApplication());
			stmt.setString(4, this.instanceKey.getEndpoint());
			stmt.setString(5, ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()));

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public void updateTransaction(TransactionArchive transaction) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("update tcc_transaction set status = ?, status_trace = ? ");
			ber.append("where application = ? and endpoint = ? and global_tx_id = ?");
			stmt = connection.prepareStatement(ber.toString());

			TransactionContext transactionContext = transaction.getTransactionContext();
			TransactionStatus transactionStatus = transaction.getTransactionStatus();
			XidImpl globalXid = transactionContext.getGlobalXid();

			stmt.setInt(1, transactionStatus.getInnerStatus());
			stmt.setInt(2, transactionStatus.getInnerStatusTrace());
			stmt.setString(3, this.instanceKey.getApplication());
			stmt.setString(4, this.instanceKey.getEndpoint());
			stmt.setString(5, ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()));

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public void completeTransaction(TransactionArchive transaction) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("update tcc_transaction set status = ?, status_trace = ? ");
			ber.append("where application = ? and endpoint = ? and global_tx_id = ?");// and branch_qualifier = ?
			stmt = connection.prepareStatement(ber.toString());

			TransactionContext transactionContext = transaction.getTransactionContext();
			TransactionStatus transactionStatus = transaction.getTransactionStatus();
			XidImpl globalXid = transactionContext.getGlobalXid();

			stmt.setInt(1, transactionStatus.getInnerStatus());
			stmt.setInt(2, transactionStatus.getInnerStatusTrace());
			stmt.setString(3, this.instanceKey.getApplication());
			stmt.setString(4, this.instanceKey.getEndpoint());
			stmt.setString(5, ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()));

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public void cleanupTransaction(TransactionArchive transaction) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("update tcc_transaction set deleted = ? ");
			ber.append("where application = ? and endpoint = ? and global_tx_id = ?");// and branch_qualifier = ?
			stmt = connection.prepareStatement(ber.toString());

			TransactionContext transactionContext = transaction.getTransactionContext();
			XidImpl globalXid = transactionContext.getGlobalXid();

			stmt.setBoolean(1, true);
			stmt.setString(2, this.instanceKey.getApplication());
			stmt.setString(3, this.instanceKey.getEndpoint());
			stmt.setString(4, ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()));

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	private void updateServiceTransaction(TransactionContext transactionContext, CompensableArchive holder) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("update tcc_compensable set try_committed = ? ");
			ber.append(", confirmed = ?, cancelled = ?, committed = ?, rolledback = ? ");
			ber.append("where application = ? and endpoint = ? and global_tx_id = ? and branch_qualifier = ?");
			stmt = connection.prepareStatement(ber.toString());

			XidImpl internalXid = holder.branchXid;

			stmt.setBoolean(1, holder.tryCommitted);
			stmt.setBoolean(2, holder.confirmed);
			stmt.setBoolean(3, holder.cancelled);
			stmt.setBoolean(4, holder.committed);
			stmt.setBoolean(5, holder.rolledback);

			stmt.setString(6, this.instanceKey.getApplication());
			stmt.setString(7, this.instanceKey.getEndpoint());
			stmt.setString(8, ByteUtils.byteArrayToString(internalXid.getGlobalTransactionId()));
			if (transactionContext.isCoordinator()) {
				stmt.setString(9, TransactionLogger.NULL);
			} else {
				stmt.setString(9, ByteUtils.byteArrayToString(internalXid.getBranchQualifier()));
			}

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	private void updateTerminatorTransaction(TransactionContext transactionContext, TerminatorArchive holder) {
		Connection connection = null;
		PreparedStatement stmt = null;
		try {
			connection = this.getConnection();

			StringBuilder ber = new StringBuilder();
			ber.append("update tcc_terminator set ");
			ber.append("prepared = ?, committed = ?, rolledback = ?, cleanup = ? ");
			ber.append("where application = ? and endpoint = ? and global_tx_id = ? ");
			ber.append("  and to_application = ? and to_endpoint = ?");
			stmt = connection.prepareStatement(ber.toString());

			XidImpl globalXid = transactionContext.getGlobalXid();
			RemoteTerminator terminator = holder.terminator;
			TerminalKey terminalKey = terminator.getTerminalKey();

			stmt.setBoolean(1, holder.prepared);
			stmt.setBoolean(2, holder.committed);
			stmt.setBoolean(3, holder.rolledback);
			stmt.setBoolean(4, holder.cleanup);
			stmt.setString(5, this.instanceKey.getApplication());
			stmt.setString(6, this.instanceKey.getEndpoint());
			stmt.setString(7, ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()));
			stmt.setString(8, terminalKey.getApplication());
			stmt.setString(9, terminalKey.getEndpoint());

			stmt.executeUpdate();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeStatement(stmt);
			this.releaseConnection(connection);
		}
	}

	@Override
	public Set<TransactionArchive> getLoggedTransactionSet() {
		Set<TransactionArchive> metadatas = new HashSet<TransactionArchive>();

		Connection connection = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			connection = this.getConnection();
			Map<XidImpl, TransactionArchive> metaMap = this.loadTransactionSet(connection);

			Map<XidImpl, CompensableArchive> servMap = this.loadNativeService(connection);
			Iterator<Map.Entry<XidImpl, CompensableArchive>> svrItr = servMap.entrySet().iterator();
			while (svrItr.hasNext()) {
				Map.Entry<XidImpl, CompensableArchive> entry = svrItr.next();
				XidImpl globalXid = entry.getKey();
				CompensableArchive holder = entry.getValue();
				XidImpl internalXid = holder.branchXid;
				TransactionArchive meta = metaMap.get(globalXid);
				Map<XidImpl, CompensableArchive> xidToNativeSvrMap = meta.getXidToNativeSvrMap();
				xidToNativeSvrMap.put(internalXid, holder);
			}

			Map<XidImpl, TerminatorArchive> terminatorMap = this.loadTerminator(connection);
			Iterator<Map.Entry<XidImpl, TerminatorArchive>> terminatorItr = terminatorMap.entrySet().iterator();
			while (terminatorItr.hasNext()) {
				Map.Entry<XidImpl, TerminatorArchive> entry = terminatorItr.next();
				XidImpl globalXid = entry.getKey();
				TerminatorArchive holder = entry.getValue();
				TransactionArchive meta = metaMap.get(globalXid);

				Map<String, TerminatorArchive> terminators = meta.getAppToTerminatorMap();
				RemoteTerminator terminator = holder.terminator;
				TerminalKey terminalKey = terminator.getTerminalKey();
				String application = terminalKey.getApplication();
				terminators.put(application, holder);
			}

			metadatas.addAll(metaMap.values());
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeResultSet(rs);
			closeStatement(stmt);
			this.releaseConnection(connection);
		}

		return metadatas;
	}

	private Map<XidImpl, CompensableArchive> loadNativeService(Connection connection) {
		Map<XidImpl, CompensableArchive> serviceMap = new HashMap<XidImpl, CompensableArchive>();
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			StringBuilder ber = new StringBuilder();
			ber.append("select s.global_tx_id, s.branch_qualifier, s.coordinator, s.bean_name, s.variable");
			ber.append(", s.try_committed, s.confirmed, s.cancelled, s.committed, s.rolledback ");
			ber.append("from tcc_compensable s ");
			ber.append("left join tcc_transaction t on (");
			ber.append("      t.application = s.application ");
			ber.append("  and t.endpoint = s.endpoint ");
			ber.append("  and t.global_tx_id = s.global_tx_id ");
			ber.append(") where s.application = ? and s.endpoint = ? and t.deleted = ?");
			stmt = connection.prepareStatement(ber.toString());
			stmt.setString(1, this.instanceKey.getApplication());
			stmt.setString(2, this.instanceKey.getEndpoint());
			stmt.setBoolean(3, false);

			rs = stmt.executeQuery();
			while (rs.next()) {

				String globalTransactionId = rs.getString("global_tx_id");
				String branchQualifier = rs.getString("branch_qualifier");
				boolean coordinator = rs.getBoolean("coordinator");
				String beanName = rs.getString("bean_name");

				CompensableArchive holder = new CompensableArchive();
				holder.launchSvc = coordinator;

				byte[] globalBytes = ByteUtils.stringToByteArray(globalTransactionId);
				XidImpl globalXid = this.xidFactory.createGlobalXid(globalBytes);
				if (coordinator) {
					holder.branchXid = globalXid;
				} else {
					byte[] branchBytes = ByteUtils.stringToByteArray(branchQualifier);
					XidImpl branchXid = this.xidFactory.createBranchXid(globalXid, branchBytes);
					holder.branchXid = branchXid;
				}

				boolean tryCommitted = rs.getBoolean("try_committed");
				boolean confirmed = rs.getBoolean("confirmed");
				boolean cancelled = rs.getBoolean("cancelled");
				boolean committed = rs.getBoolean("committed");
				boolean rolledback = rs.getBoolean("rolledback");
				holder.tryCommitted = tryCommitted;
				holder.confirmed = confirmed;
				holder.cancelled = cancelled;
				holder.committed = committed;
				holder.rolledback = rolledback;

				CompensableInfo info = new CompensableInfo();
				info.setIdentifier(beanName);
				Compensable<Serializable> service = this.compensableMarshaller.unmarshallCompensable(info);
				holder.service = service;

				Blob blob = rs.getBlob("variable");
				Serializable variable = null;
				if (blob != null) {
					InputStream input = blob.getBinaryStream();
					if (input != null) {
						byte[] bytes = this.streamToByteArray(input);
						try {
							variable = (Serializable) this.serializer.deserialize(bytes);
						} catch (IOException e) {
							// ignore
						}
					}
				}
				holder.variable = variable;

				serviceMap.put(globalXid, holder);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeResultSet(rs);
			closeStatement(stmt);
		}

		return serviceMap;
	}

	private Map<XidImpl, TerminatorArchive> loadTerminator(Connection connection) {
		Map<XidImpl, TerminatorArchive> transactionMap = new HashMap<XidImpl, TerminatorArchive>();
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			StringBuilder ber = new StringBuilder();
			ber.append("select s.global_tx_id, s.to_application, s.to_endpoint");
			ber.append(", s.prepared, s.committed, s.rolledback, s.cleanup ");
			ber.append("from tcc_terminator s ");
			ber.append("left join tcc_transaction t on (");
			ber.append("        t.application = s.application ");
			ber.append("    and t.endpoint = s.endpoint ");
			ber.append("    and t.global_tx_id = s.global_tx_id ");
			ber.append(") where s.application = ? and s.endpoint = ? and t.deleted = ?");
			stmt = connection.prepareStatement(ber.toString());
			stmt.setString(1, this.instanceKey.getApplication());
			stmt.setString(2, this.instanceKey.getEndpoint());
			stmt.setBoolean(3, false);

			rs = stmt.executeQuery();
			while (rs.next()) {
				TerminatorArchive holder = new TerminatorArchive();

				String towardsApplication = rs.getString("to_application");
				String towardsEndpoint = rs.getString("to_endpoint");
				String globalTransactionId = rs.getString("global_tx_id");
				boolean prepared = rs.getBoolean("prepared");
				boolean committed = rs.getBoolean("committed");
				boolean rolledback = rs.getBoolean("rolledback");
				boolean cleanup = rs.getBoolean("cleanup");

				byte[] globalBytes = ByteUtils.stringToByteArray(globalTransactionId);
				XidImpl globalXid = this.xidFactory.createGlobalXid(globalBytes);

				holder.prepared = prepared;
				holder.committed = committed;
				holder.rolledback = rolledback;
				holder.cleanup = cleanup;

				TerminatorInfo info = new TerminatorInfo();
				info.setApplication(towardsApplication);
				info.setEndpoint(towardsEndpoint);
				info.setBranchXid(globalXid);
				RemoteTerminator terminator = this.terminatorMarshaller.unmarshallTerminator(info);
				holder.terminator = terminator;

				transactionMap.put(globalXid, holder);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeResultSet(rs);
			closeStatement(stmt);
		}

		return transactionMap;
	}

	private Map<XidImpl, TransactionArchive> loadTransactionSet(Connection connection) {
		Map<XidImpl, TransactionArchive> metaMap = new HashMap<XidImpl, TransactionArchive>();
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			StringBuilder ber = new StringBuilder();
			ber.append("select global_tx_id, status, status_trace");
			ber.append(", coordinator, created_time ");
			ber.append("from tcc_transaction ");
			ber.append("where application = ? and endpoint = ? and deleted = ?");
			stmt = connection.prepareStatement(ber.toString());
			stmt.setString(1, this.instanceKey.getApplication());
			stmt.setString(2, this.instanceKey.getEndpoint());
			stmt.setBoolean(3, false);
			rs = stmt.executeQuery();
			while (rs.next()) {
				String globalTransactionId = rs.getString("global_tx_id");
				int state = rs.getInt("status");
				int trace = rs.getInt("status_trace");
				boolean coordinator = rs.getBoolean("coordinator");
				Timestamp createdTime = rs.getTimestamp("created_time");

				TransactionContext context = new TransactionContext();

				TerminalKey terminalKey = new TerminalKey();

				terminalKey.setApplication(this.instanceKey.getApplication());
				terminalKey.setEndpoint(this.instanceKey.getEndpoint());
				context.setTerminalKey(terminalKey);

				context.setRecovery(true);
				context.setCompensable(true);
				context.setCoordinator(coordinator);
				byte[] globalBytes = ByteUtils.stringToByteArray(globalTransactionId);
				XidImpl globalXid = this.xidFactory.createGlobalXid(globalBytes);
				// context.setGlobalXid(globalXid);
				context.setCreationXid(globalXid);
				context.setCurrentXid(globalXid);

				context.setCreatedTime(createdTime.getTime());

				TransactionStatus status = new TransactionStatus(state, trace);
				TransactionArchive meta = new TransactionArchive();
				meta.setTransactionStatus(status);
				meta.setTransactionContext(context);

				metaMap.put(globalXid, meta);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			closeResultSet(rs);
			closeStatement(stmt);
		}

		return metaMap;
	}

	private byte[] streamToByteArray(InputStream input) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ReadableByteChannel in = null;
		WritableByteChannel out = null;
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		try {
			in = Channels.newChannel(input);
			out = Channels.newChannel(baos);
			while (in.read(buffer) != -1) {
				buffer.flip();
				out.write(buffer);
				buffer.clear();
			}
		} catch (IOException ex) {
			// ignore
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					// ignore
				}
			}
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return baos.toByteArray();
	}

	public static void closeStatement(Statement stmt) {
		if (stmt != null) {
			try {
				stmt.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public static void closeResultSet(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void setCompensableMarshaller(CompensableMarshaller compensableMarshaller) {
		this.compensableMarshaller = compensableMarshaller;
	}

	public void setSerializer(ObjectSerializer serializer) {
		this.serializer = serializer;
	}

	public void setTerminatorMarshaller(TerminatorMarshaller terminatorMarshaller) {
		this.terminatorMarshaller = terminatorMarshaller;
	}

	public void setInstanceKey(TerminalKey instanceKey) {
		this.instanceKey = instanceKey;
	}

	public void setXidFactory(XidFactory xidFactory) {
		this.xidFactory = xidFactory;
	}

}
