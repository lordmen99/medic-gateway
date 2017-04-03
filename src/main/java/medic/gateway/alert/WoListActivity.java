package medic.gateway.alert;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;

import static medic.gateway.alert.GatewayLog.logException;
import static medic.gateway.alert.GatewayLog.trace;
import static medic.gateway.alert.Utils.showSpinner;
import static medic.gateway.alert.Utils.absoluteTimestamp;
import static medic.gateway.alert.WoMessage.Status.UNSENT;
import static medic.gateway.alert.WoMessage.Status.FAILED;

public class WoListActivity extends FragmentActivity {
	private static final DialogInterface.OnClickListener NO_CLICK_LISTENER = null;

	private Db db;
	private Set<String> checkedMessageIds;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.message_list_wo);

		this.db = Db.getInstance(this);

		((Button) findViewById(R.id.btnRefreshWoMessageList))
				.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { refreshList(); }
		});

		((Button) findViewById(R.id.btnRetrySelected))
				.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { retrySelected(); }
		});

		refreshList();
	}

//> API FOR WoListFragment
	boolean isChecked(WoMessage m) {
		return checkedMessageIds.contains(m.id);
	}

	void updateChecked(WoMessage m, boolean isChecked) {
		if(isChecked) checkedMessageIds.add(m.id);
		else checkedMessageIds.remove(m.id);

		findViewById(R.id.btnRetrySelected).setEnabled(!checkedMessageIds.isEmpty());
	}

	void showMessageDetailDialog(final WoMessage m) {
		final ProgressDialog spinner = showSpinner(this);
		AsyncTask.execute(new Runnable() {
			public void run() {
				try {
					LinkedList<String> content = new LinkedList<>();

					content.add(string(R.string.lblTo, m.to));
					content.add(string(R.string.lblContent, m.content));
					content.add(string(R.string.lblStatusUpdates));

					List<WoMessage.StatusUpdate> updates = db.getStatusUpdates(m);
					Collections.reverse(updates);
					for(WoMessage.StatusUpdate u : updates) {
						String status;
						if(u.newStatus == FAILED) {
							status = String.format("%s (%s)", u.newStatus, u.failureReason);
						} else {
							status = u.newStatus.toString();
						}
						content.add(String.format("%s: %s", absoluteTimestamp(u.timestamp), status));
					}

					final AlertDialog.Builder dialog = new AlertDialog.Builder(WoListActivity.this);
					if(m.status.canBeRetried()) {
						dialog.setPositiveButton(R.string.btnRetry, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								retry(m.id);
								resetScroll();
								refreshList();
							}
						});
					}

					dialog.setItems(content.toArray(new String[content.size()]), NO_CLICK_LISTENER);

					runOnUiThread(new Runnable() {
						public void run() { dialog.create().show(); }
					});
				} catch(Exception ex) {
					logException(WoListActivity.this, ex, "Failed to load WO message details.");
				} finally {
					spinner.dismiss();
				}
			}
		});
	}

//> PRIVATE HELPERS
	private void retry(String id) {
		trace(this, "Retrying message with id %s...", id);

		WoMessage m = db.getWoMessage(id);

		if(!m.status.canBeRetried()) return;

		db.updateStatus(m, UNSENT);
	}

	private final String string(int stringId, Object...args) {
		return getString(stringId, args);
	}

	private void resetScroll() {
		// This implementation is far from ideal, but at least it works.
		// Which is more than can be said for more logical options like:
		// - getFragment().setSelection(0);
		// - getFragment().getListView().setSelection(0);
		// - getFragment().getListView().setSelectionAfterHeaderView();
		// ...and doing all of the above inside an AsyncTask.
		getFragment().getListView().smoothScrollToPosition(0);
	}

	private void refreshList() {
		checkedMessageIds = new HashSet<String>();

		getSupportLoaderManager().restartLoader(WoListFragment.LOADER_ID, null, getFragment());

		findViewById(R.id.btnRetrySelected).setEnabled(false);
	}

	private void retrySelected() {
		resetScroll();

		for(String id : checkedMessageIds) retry(id);

		refreshList();
	}

	private WoListFragment getFragment() {
		return (WoListFragment) getSupportFragmentManager()
				.findFragmentById(R.id.lstWoMessages);
	}
}
