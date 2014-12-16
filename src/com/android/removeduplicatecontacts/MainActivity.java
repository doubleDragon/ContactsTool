package com.android.removeduplicatecontacts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		removeDuplicateContacts();
	}
	
	/**
	 * Start a service to remove duplicate contacts
	 * @author wsl
	 */
	private void removeDuplicateContacts() {
		Intent i = RemoveService.createRemoveDuplicateContactsIntent(this);
		startService(i);
	}

}
