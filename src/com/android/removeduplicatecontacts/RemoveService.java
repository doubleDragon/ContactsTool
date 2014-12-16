package com.android.removeduplicatecontacts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

public class RemoveService extends IntentService{
	
	private static final String TAG = RemoveService.class.getSimpleName();
	
	private static final String ACTION_REMOVE_DUPLICATE_CONTACTS = "removeDuplicateContacts";
	
	public static final String DATA_QUERY_ORDER="_id desc";

	public RemoveService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String action = intent.getAction();
		if (action.equals(ACTION_REMOVE_DUPLICATE_CONTACTS)) {
			handleRemoveDuplicateContacts(intent);
		}
	}

	public static Intent createRemoveDuplicateContactsIntent(Context context) {
		Intent intent = new Intent();
		intent.setClass(context, RemoveService.class);
		intent.setAction(ACTION_REMOVE_DUPLICATE_CONTACTS);
		return intent;
	}
	
	private void queryStep1(HashMap<String, ArrayList<ContactBean>> contactsMap) {
		final String[] PROJ_DATA = new String[] {
			Data._ID,
			Data.CONTACT_ID,
			Data.DATA1,
			Contacts.DISPLAY_NAME
		};
		Cursor dataCursor = getContentResolver().query(Data.CONTENT_URI,
				PROJ_DATA,
				Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
				null, DATA_QUERY_ORDER);
		long dataId, contactId;
		String number, name;
		PhoneBean phoneBean;
		ContactBean contactBean;
		ArrayList<ContactBean> tempSameBeans;
		try {
			dataCursor.moveToPosition(-1);
			while(dataCursor.moveToNext()) {
				dataId = dataCursor.getLong(0);
				contactId = dataCursor.getLong(1);
				number = dataCursor.getString(2);
				name = dataCursor.getString(3);
				phoneBean = new PhoneBean(dataId, number);
				contactBean = new ContactBean(contactId, name);
				contactBean.addNumber(phoneBean);
				if(contactsMap.containsKey(name)) {
					tempSameBeans = contactsMap.get(name);
					if(!tempSameBeans.contains(contactBean)) {
						tempSameBeans.add(contactBean);
					}
				} else {
					tempSameBeans = new ArrayList<ContactBean>();
					tempSameBeans.add(contactBean);
					contactsMap.put(name, tempSameBeans);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(dataCursor != null) {
				dataCursor.close();
			}
		}
	}
	
	private void queryStep2(List<PhoneBean> beans, HashMap<Long, PhoneBean> phones) {
		final String[] PROJ_DATA = new String[] {
			Data._ID,
			Data.CONTACT_ID,
			Data.DATA1,
		};
		Cursor dataCursor = getContentResolver().query(Data.CONTENT_URI,
				PROJ_DATA,
				Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
				null, DATA_QUERY_ORDER);
		long dataId, contactId;
		String number;
		PhoneBean bean;
		try {
			dataCursor.moveToPosition(-1);
			while(dataCursor.moveToNext()) {
				dataId = dataCursor.getLong(0);
				contactId = dataCursor.getLong(1);
				number = dataCursor.getString(2);
				bean = new PhoneBean(dataId, number);
				beans.add(bean);
				//same contactId save one phoneBean
				phones.put(contactId, bean);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(dataCursor != null) {
				dataCursor.close();
			}
		}
	}
	
	private void deletePhoneFromData(HashSet<Long> dataIdSet) {
		final String[] questionMarks = new String[dataIdSet.size()];
		Arrays.fill(questionMarks, "?");
		final StringBuilder sb = new StringBuilder();
		sb.append(Data._ID + " IN (");
		sb.append(TextUtils.join(",", questionMarks));
		sb.append(")");
		String[] selectionArgs = new String[dataIdSet.size()];
		int i = 0;
		for(long id : dataIdSet) {
			selectionArgs[i] = String.valueOf(id);
			i++;
		}
		getContentResolver().delete(Data.CONTENT_URI, sb.toString(), selectionArgs);
	}
	
	/**
	 * Remove some contacts for those same name but different contactId
	 * 1,Get all contactId, name and number, generate {@link ContactBean}} list
	 * 2,HashMap name and multiple {@link ContactBean}}, Map<contactId, ArrayList<ContactBean>>
	 * 3,delete contact for same name {@link ContactBean}}, insert new {@link ContactBean}}
	 * include unique numbers and name
	 */
	private void step1() {
		HashMap<String, ArrayList<ContactBean>> contactsMap = new HashMap<String, ArrayList<ContactBean>>();
		queryStep1(contactsMap);
		
		Set<String> nameSet = contactsMap.keySet();
		ArrayList<ContactBean> tempBeans;
		for(String name : nameSet) {
			tempBeans = contactsMap.get(name);
			ContactBean newBean = generateNewContact(tempBeans);
			if(newBean == null) {
				//Never happen
				Log.e(TAG, "generateNewContact null ContactBean for name: " + name);
				continue;
			}
			deleteSameNameContactsList(tempBeans);
			insertNewContact(newBean);
		}
	}
	
	private void insertNewContact(ContactBean bean) {
		if(bean == null || TextUtils.isEmpty(bean.name)) return;
		
		Log.d(TAG, "insertNewContact: " + bean);
		ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        ContentProviderOperation operation = ContentProviderOperation.newInsert(
        		RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build();
        operations.add(operation);
        
        //insert display_name
		operation = ContentProviderOperation.newInsert(Data.CONTENT_URI)
				.withValueBackReference(Data.RAW_CONTACT_ID, 0)
				.withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
				.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, bean.name)
				.build();
		operations.add(operation);
		
		//insert phones
		ArrayList<PhoneBean> phones = bean.getNumbers();
		for (PhoneBean phone : phones) {
			operation = ContentProviderOperation
					.newInsert(Data.CONTENT_URI)
					.withValueBackReference(Data.RAW_CONTACT_ID, 0)
					.withValue(
							Data.MIMETYPE,
							ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
					.withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
							ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
					.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,
							phone.number).build();

			operations.add(operation);
		}
		try {
			ContentProviderResult[] results = getContentResolver().applyBatch(
					ContactsContract.AUTHORITY, operations);
			for (ContentProviderResult result : results) {
				Log.i(TAG, result.uri.toString());
			}
		} catch (RemoteException e) {
			// Something went wrong, bail without success
			Log.e(TAG, "Problem persisting user edits", e);
		} catch (OperationApplicationException e) {
			Log.e(TAG, "Insert fails", e);
		}
		operations.clear();
	}
	
	private ContactBean generateNewContact(ArrayList<ContactBean> sameNameBeanList) {
		if(sameNameBeanList == null || sameNameBeanList.isEmpty()) return null;
		ContactBean result = sameNameBeanList.get(0);
		for(ContactBean bean : sameNameBeanList) {
			result.addNumbers(bean.getNumbers());
		}
		return result;
	}
	
	private void deleteSameNameContactsList(ArrayList<ContactBean> beans) {
		if(beans.isEmpty()) return;
		
		int size = beans.size();
		String selection;
		String[] selectionArgs = new String[size];
		if(size == 1) {
			ContactBean bean = beans.get(0);
			selection = RawContacts.CONTACT_ID + " = ?";
			selectionArgs[0] = String.valueOf(bean.contactId);
			return;
		} else {
			final String[] questionMarks = new String[size];
			Arrays.fill(questionMarks, "?");
			StringBuilder sb = new StringBuilder();
			sb.append(RawContacts.CONTACT_ID + " IN (");
			sb.append(TextUtils.join(",", questionMarks));
			sb.append(")");
			selection = sb.toString();
			int i=0;
			for (ContactBean bean : beans) {
				selectionArgs[i] = String.valueOf(bean.contactId);
				i++;
			}
		}
		deleteInternal(selection, selectionArgs);
	}
	
	private void deleteInternal(String selection, String[] selectionArgs) {
		Log.d(TAG, "deleteInternal contact selection: " + selection + " selectionArgs: "
				+ TextUtils.join(",", selectionArgs));
		getContentResolver().delete(ContactsContract.RawContacts.CONTENT_URI, selection, selectionArgs);
	}
	
	/**
	 * Delete duplicate phone numbers in same contact id
	 * 1, Get contacts data from provider,number and raw_contact_id and contactId
	 * 2, Generate id list for those same contact_id and number
	 * 3, Delete raw_contacts base on raw_contact_id
	 * 
	 */
	private void step2() {
		List<PhoneBean> beans = new ArrayList<PhoneBean>();
		HashMap<Long, PhoneBean> phones = new HashMap<Long, PhoneBean>();
		queryStep2(beans, phones);
		if(beans.isEmpty() || phones.isEmpty()) {
			//do none
			return;
		}
		HashSet<Long> dataIdSet = new HashSet<Long>();
		for(PhoneBean bean : beans) {
			if(phones.containsValue(bean)) {
				continue;
			}
			dataIdSet.add(bean.dataId);
		}
		
		if(dataIdSet.isEmpty()) {
			//no duplicate phone number
			Log.d("test", "duplicate contacts empty");
			return;
		}
		deletePhoneFromData(dataIdSet);
	}
	
	/**
	 * First, remove same contacts for those same name and multiple contactId
	 * 1,Get all contactId, name and number, generate contactbean list
	 * 2,HashMap name and multiple contactbean, Map<contactId, ArrayList<ContactBean>
	 * 3,delete contact for same name ContactBean, insert new ContactBean include unique numbers and name
	 * Then,it take three steps for those one contactId and multiple phone number
	 * 1, Get contacts data from provider,number and raw_contact_id and contactId
	 * 2, Generate id list for those same contact_id and number
	 * 3, Delete raw_contacts base on raw_contact_id
	 * @author wsl
	 */
	private void handleRemoveDuplicateContacts(Intent intent) {
		step1();
		step2();
	}
}
