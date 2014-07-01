package cc.landking.development.tools.mvn_com_overide;

import java.util.ResourceBundle;

public class Messages {
	static ResourceBundle bundle = ResourceBundle.getBundle("cc.landking.development.tools.mvn_com_overide.messages");
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO

	}
	public static String getString(String key){
		try{
			return bundle.getString(key);
		}catch(Exception ex){
			return key;
		}
	}

}
