package analysethis.returningpassedobjects;

public class ChainedInit {
	private String	first;
	private String	second;

	public ChainedInit setFirst(String first) {
		this.first = first;
		return this;
	}

	public ChainedInit setSecond(String second) {
		this.second = second;
		return this;
	}

	public String getBoth() {
		return first + second;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((first == null) ? 0 : first.hashCode());
		result = prime * result + ((second == null) ? 0 : second.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ChainedInit other = (ChainedInit) obj;
		if (first == null) {
			if (other.first != null) {
				return false;
			}
		} else if (!first.equals(other.first)) {
			return false;
		}
		if (second == null) {
			if (other.second != null) {
				return false;
			}
		} else if (!second.equals(other.second)) {
			return false;
		}
		return true;
	}
}
