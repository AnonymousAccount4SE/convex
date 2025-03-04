package convex.gui.manager.windows.peer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.Convex;
import convex.core.Result;
import convex.core.crypto.AKeyPair;
import convex.core.data.ACell;
import convex.core.data.AVector;
import convex.core.data.Address;
import convex.core.data.Strings;
import convex.core.lang.Reader;
import convex.core.transactions.ATransaction;
import convex.core.transactions.Invoke;
import convex.core.transactions.Multi;
import convex.core.util.Text;
import convex.core.util.Utils;
import convex.gui.components.ActionPanel;
import convex.gui.manager.PeerGUI;
import convex.gui.utils.Toolkit;

@SuppressWarnings("serial")
public class StressPanel extends JPanel {
	static final Logger log = LoggerFactory.getLogger(StressPanel.class.getName());

	protected Convex peerConvex;

	private ActionPanel actionPanel;

	private JButton btnRun;

	private JSpinner requestCountSpinner;
	private JSpinner transactionCountSpinner;
	private JSpinner opCountSpinner;
	private JSpinner clientCountSpinner;
	private JCheckBox syncCheckBox;
	private JCheckBox distCheckBox;
	
	private JSplitPane splitPane;
	private JPanel resultPanel;
	private JTextArea resultArea;
	
	private JComboBox<String> txTypeBox;

	public StressPanel(Convex peerView) {
		this.peerConvex = peerView;
		this.setLayout(new BorderLayout());

		actionPanel = new ActionPanel();
		add(actionPanel, BorderLayout.SOUTH);

		btnRun = new JButton("Run Test");
		actionPanel.add(btnRun);
		btnRun.addActionListener(e -> {
			btnRun.setEnabled(false);
			SwingUtilities.invokeLater(() -> runStressTest());
		});

		splitPane = new JSplitPane();
		add(splitPane, BorderLayout.CENTER);

		JPanel panel = new JPanel();
		splitPane.setLeftComponent(panel);
		FlowLayout flowLayout = (FlowLayout) panel.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		flowLayout.setAlignOnBaseline(true);

		// =========================================
		// Option Panel

		JPanel optionPanel = new JPanel();
		panel.add(optionPanel);
		optionPanel.setLayout(new GridLayout(0, 2, 0, 0));

		JLabel lblClients = new JLabel("Clients");
		optionPanel.add(lblClients);
		clientCountSpinner = new JSpinner();
		// Note: about 300 max number of clients before hitting juice limits for account creation
		clientCountSpinner.setModel(new SpinnerNumberModel(100, 1, 1000, 1));
		optionPanel.add(clientCountSpinner);

		JLabel lblRequests = new JLabel("Requests per client");
		optionPanel.add(lblRequests);
		requestCountSpinner = new JSpinner();
		requestCountSpinner.setModel(new SpinnerNumberModel(100, 1, 1000000, 10));
		optionPanel.add(requestCountSpinner);

		JLabel lblTrans = new JLabel("Transactions per Request");
		optionPanel.add(lblTrans);
		transactionCountSpinner = new JSpinner();
		transactionCountSpinner.setModel(new SpinnerNumberModel(10, 1, 1000, 1));
		optionPanel.add(transactionCountSpinner);

		JLabel lblOps = new JLabel("Ops per Transaction");
		optionPanel.add(lblOps);
		opCountSpinner = new JSpinner();
		opCountSpinner.setModel(new SpinnerNumberModel(1, 1, 1000, 10));
		optionPanel.add(opCountSpinner);
		
		JLabel lblSync=new JLabel("Sync Requests?");
		optionPanel.add(lblSync);
		syncCheckBox=new JCheckBox();
		optionPanel.add(syncCheckBox);
		syncCheckBox.setSelected(true);
		
		JLabel lblDist=new JLabel("Distribute over Peers?");
		optionPanel.add(lblDist);
		distCheckBox=new JCheckBox();
		optionPanel.add(distCheckBox);
		distCheckBox.setSelected(false);

		JLabel lblTxType=new JLabel("Transaction Type");
		txTypeBox=new JComboBox<String>();
		txTypeBox.addItem("Define Data");
		// txTypeBox.addItem("Transfer");
		// txTypeBox.addItem("AMM Trade");
		txTypeBox.addItem("Null Op");
		optionPanel.add(lblTxType);
		optionPanel.add(txTypeBox);


		// =========================================
		// Result Panel

		resultPanel = new JPanel();
		splitPane.setRightComponent(resultPanel);
		resultPanel.setLayout(new BorderLayout(0, 0));

		resultArea = new JTextArea();
		resultArea.setText("No results yet");
		resultArea.setLineWrap(true);
		resultArea.setEditable(false);
		resultPanel.add(resultArea);
		resultArea.setFont(Toolkit.SMALL_MONO_FONT);
	}

	NumberFormat formatter = new DecimalFormat("#0.000");

	private final class StressTest extends SwingWorker<String, Object> {
		long errors = 0;
		long values = 0;
		private final AKeyPair kp;
		private final Address address;
		int transCount = (Integer) transactionCountSpinner.getValue();
		int requestCount = (Integer) requestCountSpinner.getValue();
		int opCount = (Integer) opCountSpinner.getValue();
		// TODO: enable multiple clients
		int clientCount = (Integer) clientCountSpinner.getValue();
		String type=(String) txTypeBox.getSelectedItem();
		ArrayList<AKeyPair> kps=new ArrayList<>(clientCount);
		ArrayList<Convex> clients=new ArrayList<>(clientCount);
		InetSocketAddress sa = peerConvex.getHostAddress();

		private StressTest(AKeyPair kp, Address address) {
			this.kp = kp;
			this.address = address;
		}

		@Override
		protected String doInBackground() throws Exception {
			StringBuilder sb = new StringBuilder();
			try {
				resultArea.setText("Connecting clients...");

				// Use client store
				// Stores.setCurrent(Stores.CLIENT_STORE);
				ArrayList<CompletableFuture<Result>> frs=new ArrayList<>();
				Convex pc = Convex.connect(sa, address,kp);
			
				// Generate client accounts
				StringBuilder cmdsb=new StringBuilder();
				cmdsb.append("(let [f (fn [k] (let [a (create-account k)] (transfer a 1000000000) a))] ");
				cmdsb.append("  (mapv f [");
				for (int i=0; i<clientCount; i++) {
					AKeyPair kp=AKeyPair.generate();
					kps.add(kp);
					cmdsb.append(" "+kp.getAccountKey());
				}
				cmdsb.append("]))");
				
				Result ccr=pc.transactSync(Invoke.create(address, -1, cmdsb.toString()));
				if (ccr.isError()) throw new Error("Creating accounts failed: "+ccr);
				AVector<Address> clientAddresses=ccr.getValue();

				connectClients(clientAddresses);
				setupClients();
				
				resultArea.setText("Syncing...");
				// Make sure we are in consensus
				pc.transactSync(Invoke.create(address, -1, Strings.create("sync")));
				long startTime = Utils.getCurrentTimestamp();
				
				resultArea.setText("Sending transactions...");
				
				ArrayList<CompletableFuture<Object>> cfutures=Utils.threadMap (cc->{
					try {
						for (int i = 0; i < requestCount; i++) {
							Address origin=cc.getAddress();
							ATransaction t = buildTransaction(origin, i);
							
							CompletableFuture<Result> fr;
							if (syncCheckBox.isSelected()) {
								Result r=cc.transactSync(t);
								fr=CompletableFuture.completedFuture(r);
							} else {	
								fr = cc.transact(t);
							}
							synchronized(frs) {
								// synchronised so we don't collide with other threads
								frs.add(fr);
							}
						}
					} catch (Exception e) {
						throw Utils.sneakyThrow(e);
					}
					return null;
				},clients);
				
				// wait for everything to be sent
				for (int i=0; i<clientCount; i++) {
					cfutures.get(i).get();
				}
				// long sendTime = Utils.getCurrentTimestamp();

				int futureCount=frs.size();
				resultArea.setText("Awaiting "+futureCount+" results...");
				

				List<Result> results = Utils.completeAll(frs).get();
				long endTime = Utils.getCurrentTimestamp();

				HashMap<ACell, Integer> errorMap=new HashMap<>();
				for (Result r : results) {
					if (r.isError()) {
						errors++;
						Utils.histogramAdd(errorMap,r.getErrorCode());
					} else {
						values++;
					}
				}
				
				for (int i=0; i<clientCount; i++) {
					clients.get(i).close();
				}

				Thread.sleep(100); // wait for state update to be reflected

				long totalCount=clientCount*transCount*requestCount;
				sb.append("Results for " + Text.toFriendlyNumber(totalCount) + " transactions\n");
				sb.append(values + " values received\n");
				sb.append(errors + " errors received\n");
				if (errors>0) {
					sb.append(errorMap);
					sb.append("\n");
				}
				
				double time=(endTime - startTime) * 0.001;
				sb.append("\n");
				sb.append("Total time:     " + formatter.format(time) + "s\n");
				sb.append("\n");
				
				sb.append("Approx TPS:     " + Text.toFriendlyIntString(totalCount/time) + "\n");
				sb.append("Approx OPS:     " + Text.toFriendlyIntString(opCount*totalCount/time) + "\n");


			} catch (Throwable e) {
				log.warn("Stress test worker terminated unexpectedly",e);
				resultArea.setText("Test Error: "+e);
			} finally {
				btnRun.setEnabled(true);
			}

			String report = sb.toString();
			return report;
		}

		private void setupClients() throws IOException {
			for (Convex c: clients) {
				String code=null;
				switch (type) {
					case "AMM Trade": code="nil"; break; 
					default: break;
				}
				if (code!=null) c.transact(code);
			}
		}

		protected void connectClients(AVector<Address> clientAddresses) throws IOException, TimeoutException {
			for (int i=0; i<clientCount; i++) {
				AKeyPair kp=kps.get(i);
				Address clientAddr = clientAddresses.get(i);
				Convex cc;
				if (distCheckBox.isSelected()) {
					InetSocketAddress pa=PeerGUI.getRandomServer().getHostAddress();
					cc=Convex.connect(pa,clientAddr,kp);
				} else {
					cc=Convex.connect(sa,clientAddr,kp);
				}
				clients.add(cc);
			}
		}

		protected ATransaction buildTransaction(Address origin, int reqNo) {
			ATransaction[] trxs=new ATransaction[transCount];
			for (int k=0; k<transCount; k++) {
					trxs[k]=buildSubTransaction(reqNo, k, origin);
			}
			ATransaction t;
			if (transCount!=1) {
				t=Multi.create(origin, -1, Multi.MODE_ANY, trxs);
			} else {
				t=trxs[0];
			}
			return t;
		}

		protected ATransaction buildSubTransaction(int reqNo, int txNo, Address origin) {
			
			StringBuilder tsb = new StringBuilder();
			if (opCount>1) tsb.append("(do ");
			for (int j = 0; j < opCount; j++) {
				switch(type) {
					case "Define Data": tsb.append("(def a"+j+" "+reqNo+") "); break;
					case "Null Op": tsb.append("nil "); break;
				}
			}
			if (opCount>1) tsb.append(")");
			String source = tsb.toString();
			ATransaction t = Invoke.create(origin,-1, Reader.read(source));
			return t;
		}

		@Override
		protected void done() {
			try {
				resultArea.setText(get());
			} catch (Exception e) {
				resultArea.setText(e.getMessage());
			}
		}
	}

	private synchronized void runStressTest() {
		Address address=peerConvex.getAddress();
		AKeyPair kp=peerConvex.getKeyPair();

		new StressTest(kp, address).execute();
	}
}
