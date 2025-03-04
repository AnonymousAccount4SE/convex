package convex.gui.manager.mainpanels;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

import convex.core.data.ACell;
import convex.core.data.Blob;
import convex.core.data.Format;
import convex.core.exceptions.ParseException;
import convex.core.lang.RT;
import convex.core.lang.Reader;
import convex.core.util.Utils;
import convex.gui.components.ActionPanel;
import convex.gui.manager.PeerGUI;
import convex.gui.utils.Toolkit;

@SuppressWarnings("serial")
public class MessageFormatPanel extends JPanel {

	final JTextArea dataArea;
	final JTextArea messageArea;
	private JPanel buttonPanel;
	protected PeerGUI manager;
	private JButton clearButton;
	private JPanel upperPanel;
	private JPanel instructionsPanel;
	private JLabel lblNewLabel;
	private JTextArea hashLabel;

	private static String HASHLABEL = "Hash: ";

	public MessageFormatPanel(PeerGUI manager) {
		this.manager = manager;
		setLayout(new BorderLayout(0, 0));

		JSplitPane splitPane = new JSplitPane();
		splitPane.setOneTouchExpandable(true);
		splitPane.setResizeWeight(0.5);
		splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		add(splitPane, BorderLayout.CENTER);

		// Top panel component
		upperPanel = new JPanel();
		upperPanel.setLayout(new BorderLayout(0, 0));
		dataArea = new JTextArea();
		dataArea.setToolTipText("Enter data objects here");
		upperPanel.add(dataArea, BorderLayout.CENTER);
		dataArea.setFont(Toolkit.MONO_FONT);
		dataArea.setLineWrap(true);
		dataArea.getDocument().addDocumentListener(Toolkit.createDocumentListener(() -> updateData()));

		// Bottom panel component
		JPanel lowerPanel = new JPanel();
		lowerPanel.setLayout(new BorderLayout(0, 0));

		messageArea = new JTextArea();
		messageArea.setToolTipText("Enter binary hex representation here");
		messageArea.setFont(Toolkit.MONO_FONT);
		lowerPanel.add(messageArea, BorderLayout.CENTER);

		splitPane.setRightComponent(lowerPanel);

		hashLabel = new JTextArea();
		hashLabel.setRows(2);
		hashLabel.setToolTipText("Hash code of the data object's serilaised representation = Data Object ID");
		hashLabel.setBorder(null);
		hashLabel.setBackground(null);
		hashLabel.setFont(Toolkit.SMALL_MONO_FONT);
		lowerPanel.add(hashLabel, BorderLayout.SOUTH);
		messageArea.getDocument().addDocumentListener(Toolkit.createDocumentListener(() -> updateMessage()));

		splitPane.setLeftComponent(upperPanel);

		buttonPanel = new ActionPanel();
		add(buttonPanel, BorderLayout.SOUTH);

		clearButton = new JButton("Clear");
		clearButton.setToolTipText("Press to clear the input areas");
		buttonPanel.add(clearButton);

		instructionsPanel = new JPanel();
		add(instructionsPanel, BorderLayout.NORTH);

		lblNewLabel = new JLabel("Use this fine tool to convert data values to encoded binary representations, and vice versa");
		instructionsPanel.add(lblNewLabel);
		clearButton.addActionListener(e -> {
			dataArea.setText("");
			messageArea.setText("");
		});
	}

	private void updateMessage() {
		if (!messageArea.isFocusOwner()) return; // prevent mutual recursion
		String data = "";
		String msg = messageArea.getText();
		try {
			Blob b = Blob.fromHex(Utils.stripWhiteSpace(msg));
			ACell o = Format.read(b);
			data = Utils.print(o);
			updateHashLabel(o);
		} catch (ParseException e) {
			data = "Unable to interpret message: " + e.getMessage();
			clearHashLabel();
		} catch (Exception e) {
			data = e.getMessage();
		}
		dataArea.setText(data);
	}

	private void clearHashLabel() {
		hashLabel.setText("Type: <none>\n"+HASHLABEL + "<invalid>");
	}

	private void updateData() {
		if (!dataArea.isFocusOwner()) return; // prevent mutual recursion
		String msg = "";
		String data = dataArea.getText();
		clearHashLabel();
		if (!data.isBlank()) try {
			messageArea.setEnabled(false);
			ACell o = Reader.read(data);
			Blob b = Format.encodedBlob(o);
			updateHashLabel(o);
			msg = b.toHexString();
			messageArea.setEnabled(true);
		} catch (Exception e) {
			msg = e.toString();
		}
		messageArea.setText(msg);
	}
	
	private void updateHashLabel(ACell v) {
		Blob b = Format.encodedBlob(v);
		StringBuilder sb=new StringBuilder();
		sb.append("Type: "+RT.getType(v));
		sb.append("\n");
		sb.append(HASHLABEL + b.getContentHash().toString());
		hashLabel.setText(sb.toString());
	}

}
