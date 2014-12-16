package com.android.removeduplicatecontacts;

import java.util.ArrayList;

public class ContactBean {
	
	public final long contactId;
	public final String name;
	private ArrayList<PhoneBean> mNumbers;
	
	public ContactBean(long contactId, String name) {
		this.contactId = contactId;
		this.name = name;
		mNumbers = new ArrayList<PhoneBean>();
	}

	public ArrayList<PhoneBean> getNumbers() {
		return mNumbers;
	}

	public void addNumbers(ArrayList<PhoneBean> beans) {
		for(PhoneBean bean : beans){
			addNumber(bean);
		}
	}
	
	public void addNumber(PhoneBean bean) {
		if(mNumbers.contains(bean)) return;
		mNumbers.add(bean);
	}

	@Override
	public boolean equals(Object o) {
		// TODO Auto-generated method stub
		if(this == o) {
			return true;
		}

		if(!(o instanceof PhoneBean)) {
			return false;
		}
		
		ContactBean lhs = (ContactBean) o;
		return contactId == lhs.contactId &&
				name == lhs.name &&
				mNumbers.equals(lhs.mNumbers);
	}

	@Override
	public int hashCode() {
		// Start with a non-zero constant.
		int result = 17;

		// Include a hash for each field.
		result = 31 * result + (int) (contactId ^ (contactId >>> 32));

		result = 31 * result + name.hashCode();

		result = 31 * result + mNumbers.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "[contactId: " + contactId + " name: " + name + 
				" numbers: " + mNumbers + "]";
	}

}
