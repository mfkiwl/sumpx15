/*
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or (at
 *  your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *
 */
package org.sump.analyzer;

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.Timer;

// TODO: DERIVED FROM ADC, BUT LOTS OF THINGS HAVE TO BE RE-DONE

/**
 * GUI Component that allows the user to control the device and start Sends.
 * <p>
 * Its modelled after JFileChooser and should allow for non-dialog implementations
 * making it somewhat reusable.
 * 
 * @version 0.3
 * @author Michael "Mr. Sump" Poppitz
 *
 */
public class DACController extends JComponent implements ActionListener, Runnable {
	public final static int ABORTED = 0;
	public final static int DATA_WRITE = 1;
	
	/**
	 * Creates an array of check boxes, adds it to the device controller and returns it.
	 * @param label label to use on device controller component
	 * @return array of created check boxes
	 */
	private JCheckBox[] createChannelList(String label) {
		JCheckBox[] boxes = new JCheckBox[32];

		for (int row = 0; row < 4; row++) {
			if (row == 0)
				add(new JLabel(label));
			else
				add(new JLabel());
	
			Container container = new Container();
			container.setLayout(new GridLayout(1, 8));
	
			for (int col = 0; col < 8; col++) {
				JCheckBox box = new JCheckBox();
				box.setEnabled(false);
				container.add(box);
				boxes[row * 8 + col] = box;
			}
	
			add(container);
		}
		return (boxes);
	}
	
	/**
	 * Constructs device controller component.
	 *
	 */
	public DACController() {
		super();
		setLayout(new GridLayout(16, 2, 5, 5));
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		device = new Device();

		String[] ports = device.getPorts();
		portSelect = new JComboBox(ports);
		add(new JLabel("Analyzer Port:"));
		add(portSelect);
		
		String[] speeds = {
			"125MHz"
		};
		speedSelect = new JComboBox(speeds);
		speedSelect.addActionListener(this);
		add(new JLabel("Sampling Rate:"));
		add(speedSelect);
		
		String[] sizes = {
			"256K", "128K", "64K", "32K", "16K", "8K", "4K",
			"2K", "1K", "512", "256", "128", "64"
		};
		sizeSelect = new JComboBox(sizes);
		sizeSelect.setSelectedIndex(7);
		add(new JLabel("Recording Size:"));
		add(sizeSelect);

		progress = new JProgressBar(0, 100);
		add(new JLabel("Progress:"));
		add(progress);
		
		JButton go = new JButton("Send");
		go.addActionListener(this);
		add(go);

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(this);
		add(cancel);

		SendData = null;
		timer = null;
		worker = null;
		status = ABORTED;
	}

	/**
	 * Internal method that initializes a dialog and add this component to it.
	 * @param frame owner of the dialog
	 */
	private void initDialog(JFrame frame) {
		// check if dialog exists with different owner and dispose if so
		if (dialog != null && dialog.getOwner() != frame) {
			dialog.dispose();
			dialog = null;
		}
		// if no valid dialog exists, create one
		if (dialog == null) {
			dialog = new JDialog(frame, "Send", true);
			dialog.getContentPane().add(this);
			dialog.pack();
		}
		// reset progress bar
		progress.setValue(0);

	}

	/**
	 * Return the device data of the last successful run.
	 * 
	 * @return device data
	 */
	public SendData getDeviceData() {
		return (SendData);
	}

	/**
	 * Extracts integers from strings regardless of trailing trash.
	 * 
	 * @param s string to be parsed
	 * @return integer value, 0 if parsing fails
	 */
	private int smartParseInt(String s) {
		int val = 0;
	
		try {
			for (int i = 1; i <= s.length(); i++)
				val = Integer.parseInt(s.substring(0, i));
		} catch (NumberFormatException E) {}
		
		return (val);
	}

	/**
	 * Starts capturing from device. Should not be called externally.
	 */
	public void run()  {
		String value = (String)portSelect.getSelectedItem();
		// TODO: need to check if attach was successful
		device.attach(value);
		
		value = (String)speedSelect.getSelectedItem();
		int f = smartParseInt(value);
		if (value.indexOf("M") > 0)
			f *= 1000000;
		else if (value.indexOf("k") > 0)
			f *= 1000;
		System.out.println("Rate: " + f);
		device.setRate(f);
		
		value = (String)sizeSelect.getSelectedItem();
		int s = smartParseInt(value);
		if (value.indexOf("K") > 0)
			s *= 1024;
		System.out.println("Size: " + s);
		device.setSize(s);
		
		value = (String)ratioSelect.getSelectedItem();
		double r = 0.5;
		if (value.equals("100/0")) r = 0;
		else if (value.equals("25/75")) r = 0.25;
		else if (value.equals("50/50")) r = 0.5;
		else if (value.equals("75/25")) r = 0.75;
		else if (value.equals("0/100")) r = 1;
		System.out.println("Ratio: " + r);
		device.setRatio(r);
	
		try {
		  if(getExternalData == false)
			SendData = device.send();
		  else {
			
			  SendData = externalSendData;
			  getExternalData = false;
		  }
		} catch (Exception ex) {
			// TODO: could make sense to also return half read Sends if array length is corrected
			SendData = null;
			ex.printStackTrace(System.out);
		}
		device.detach();

		status = DATA_WRITE;
	}
	

	/**
	 * Sets the enabled state of all configuration components of the dialog.
	 * @param enable <code>true</code> to enable components, <code>false</code> to disable them
	 */
	private void setDialogEnabled(boolean enable) {
		portSelect.setEnabled(enable);
		speedSelect.setEnabled(enable);
		sizeSelect.setEnabled(enable);
		ratioSelect.setEnabled(enable);
	}

	/**
	 * Properly closes the dialog.
	 * This method makes sure timer and worker thread are stopped before the dialog is closed.
	 *
	 */
	private void close() {
		if (timer != null) {
			timer.stop();
			timer = null;
		}
		if (worker != null) {
			worker.interrupt();
			worker = null;
		}
		dialog.hide();
	}
	
	/**
	 * Starts the Send thread.
	 */
	private void startSend() {
		try {
			setDialogEnabled(false);
			timer = new Timer(100, this);
			worker = new Thread(this);
			timer.start();
			worker.start();
		} catch(Exception E) {
			E.printStackTrace(System.out);
		}
	}
	
	/**
	 * Handles all action events for this component.
	 */ 
	public void actionPerformed(ActionEvent event) {
		Object o = event.getSource();
		String l = event.getActionCommand();
		
		if (o == timer) {
			if (status == DATA_WRITE) {
				close();
			} else {
				if(device.isRunning())
					progress.setValue(device.getPercentage());
			}
		} else {
		
			if (l.equals("Send")) {
				startSend();
			} else {
				close();
			}
		}
	}
	
	/**
	 * Displays the device controller dialog with enabled configuration portion and waits for user input.
	 * 
	 * @param frame parent frame of this dialog
	 * @return status, which is either <code>ABORTED</code> or <code>DATA_READ</code>
	 * @throws Exception
	 */
	public int showSendialog(JFrame frame) throws Exception {
		status = ABORTED;
		initDialog(frame);
		setDialogEnabled(true);
		dialog.show();
		return status;
	}

	/**
	 * Displays the device controller dialog with disabled configuration, starting Send immediately.
	 * 
	 * @param frame parent frame of this dialog
	 * @return status, which is either <code>ABORTED</code> or <code>DATA_READ</code>
	 * @throws Exception
	 */
	public int showSendProgress(JFrame frame) throws Exception {
		status = ABORTED;
		initDialog(frame);
		startSend();
		dialog.show();
		return status;
	}

	private Thread worker;
	private Timer timer;
	
	private JComboBox portSelect;
	private JComboBox speedSelect;
	private JComboBox sizeSelect;
	private JComboBox ratioSelect;
	private JProgressBar progress;
	
	private JDialog dialog;
	private Device device;
	private SendData SendData;
	
	static public SendData externalSendData = null;
	static public boolean getExternalData = false; 
	
	private int status;
	
	private static final long serialVersionUID = 1L;
}
