package com.android.removeduplicatecontacts;

final class PhoneBean {

	public final long dataId;
	public final String number;

	public PhoneBean(long dataId, String number) {
		this.dataId = dataId;
		this.number = number;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}

		if(!(o instanceof PhoneBean)) {
			return false;
		}
		
		PhoneBean lhs = (PhoneBean) o;
		return dataId == lhs.dataId &&
			(number == null ? lhs.number == null : number.equals(lhs.number));
	}

	@Override
	public int hashCode() {
		// Start with a non-zero constant.
		int result = 17;

		// Include a hash for each field.
		result = 31 * result + (int) (dataId ^ (dataId >>> 32));

		result = 31 * result + number.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "[dataId: " + dataId + " number: " + number + "]";
	}
}
