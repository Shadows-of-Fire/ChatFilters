package shadows.filters;

import com.google.gson.annotations.Expose;

public class Replacement {

	@Expose
	private final String word, replace;

	public Replacement(String left, String right) {
		this.word = left;
		this.replace = right;
	}

	public String getWord() {
		return word;
	}

	public String getReplace() {
		return replace;
	}

}
