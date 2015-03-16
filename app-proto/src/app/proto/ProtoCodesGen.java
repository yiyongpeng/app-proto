package app.proto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * 消息编号生成器
 * 
 * @author yiyongpeng
 * 
 */
public class ProtoCodesGen {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err
					.println("args: {messageFileName} {templateFileName} [charsetName]");
			System.err.println("  eg: Messages.proto templates/java.xml utf-8");
			return;
		}
		String messageFileName = args[0];
		String templateFileName = args[1];
		String charsetName = args.length > 2 ? args[2] : "utf-8";

		File messageFile = new File(messageFileName);
		File templateFile = new File(templateFileName);

		if (messageFile.exists() == false) {
			System.err.println(messageFile + " Not found!");
			return;
		}
		if (templateFile.exists() == false) {
			System.err.println(templateFile + " Not found!");
			return;
		}

		BufferedReader mreader = new BufferedReader(new InputStreamReader(
				new FileInputStream(messageFile), "GBK"));
		PrintWriter out = null;
		try {
			SAXReader xml = new SAXReader();
			Document doc = xml.read(templateFile);
			Element root = doc.getRootElement();
			String line = null;

			Element destPath = root.element("build-path");
			if (destPath == null) {
				System.err.println(templateFile
						+ ": Not found tag <build-path>");
				return;
			}

			String destFileName = parseHeadTemplate(destPath.getTextTrim(),
					messageFileName, templateFileName, charsetName);
			File destFile = new File(destFileName);
			mkdir(destFile);
			out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(
					destFile), charsetName));

			Element headTemplate = root.element("code-head");
			if (headTemplate != null) {
				out.println(parseHeadTemplate(headTemplate.getText(),
						messageFileName, templateFileName, charsetName));
			}
			StringBuilder note = new StringBuilder();
			int lineNum = 0;
			Map<String, Integer> countorMap = new HashMap<String, Integer>();
			while ((line = mreader.readLine()) != null) {
				if (line.startsWith(" ") | line.startsWith("\t"))
					continue;
				line = line.trim();
				if (line.startsWith("//")) {
					note.append(line.substring(2)).append("\n");
					continue;
				}
				String[] params = line.split("\\s+");
				if (params.length < 2)
					continue;

				lineNum++;

				String type = params[0];
				String name = params[1];

				Element typeEle = root.element("code-" + type);
				if (typeEle != null) {
					Integer typeNum = countorMap.get(type);
					if (typeNum == null) {
						typeNum = 0;
						String start = typeEle.attributeValue("start");
						if (start != null && !start.equals("")) {
							typeNum = Integer.parseInt(start.trim());
						}
					} else
						typeNum++;
					String sigleCode = parseSingleTemplate(typeEle.getText(),
							type, name, lineNum, typeNum, note.toString()
									.trim());
					out.println(sigleCode);
					countorMap.put(type, typeNum);
				}
				note.setLength(0);
			}
			Element footerEle = root.element("code-footer");
			if (footerEle != null) {
				String templateStr = footerEle.getText();
				String footerStr = parseFooterTemplate(templateStr,
						messageFileName, templateFileName, charsetName);
				out.println(footerStr);
			}
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (out != null)
				out.close();
			try {
				mreader.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static String parseFooterTemplate(String templateStr,
			String messageFileName, String templateFileName, String charsetName) {
		templateStr = parseHeadTemplate(templateStr, messageFileName,
				templateFileName, charsetName);

		return templateStr;
	}

	private static String parseSingleTemplate(String templateStr, String type,
			String name, int lineNum, int typeNum, String note) {
		templateStr = templateStr.replaceAll("\\$\\{note\\}", note);
		templateStr = templateStr.replaceAll("\\$\\{name\\}",
				name.substring(0, 1).toLowerCase() + name.substring(1));
		templateStr = templateStr.replaceAll("\\$\\{Name\\}",
				name.substring(0, 1).toUpperCase() + name.substring(1));
		templateStr = templateStr.replaceAll("\\$\\{NAME\\}",
				name.toUpperCase());
		templateStr = templateStr.replaceAll("\\$\\{line\\}",
				String.valueOf(lineNum));
		templateStr = templateStr.replaceAll("\\$\\{i\\}",
				String.valueOf(typeNum));

		return templateStr;
	}

	private static String parseHeadTemplate(String templateStr,
			String messageFileName, String templateFileName, String charsetName) {
		File msgFile = new File(messageFileName);
		File tempFile = new File(templateFileName);

		String messagesName = msgFile.getName().substring(0,
				msgFile.getName().lastIndexOf('.'));
		String templateName = tempFile.getName().substring(0,
				tempFile.getName().lastIndexOf('.'));

		templateStr = templateStr.replaceAll("\\$\\{messagesFileName\\}",
				msgFile.toString().replaceAll("\\\\", "/"));
		templateStr = templateStr.replaceAll("\\$\\{templateFileName\\}",
				tempFile.toString().replaceAll("\\\\", "/"));

		templateStr = templateStr.replaceAll("\\$\\{fileName\\}", messagesName
				.substring(0, 1).toLowerCase() + messagesName.substring(1));
		templateStr = templateStr.replaceAll("\\$\\{FileName\\}", messagesName
				.substring(0, 1).toUpperCase() + messagesName.substring(1));
		templateStr = templateStr.replaceAll(
				"\\$\\{templateName\\}",
				templateName.substring(0, 1).toLowerCase()
						+ templateName.substring(1));
		templateStr = templateStr.replaceAll(
				"\\$\\{TemplateName\\}",
				templateName.substring(0, 1).toUpperCase()
						+ templateName.substring(1));
		templateStr = templateStr.replaceAll("\\$\\{charsetName\\}",
				charsetName);

		templateStr = templateStr.replaceAll("\\$\\{lg\\}", "<");
		templateStr = templateStr.replaceAll("\\$\\{gt\\}", ">");

		return templateStr;
	}

	private static void mkdir(File file) {
		File parentFile = file.getParentFile();
		if (parentFile != null) {
			if (parentFile.exists() == false) {
				mkdir(parentFile);
				parentFile.mkdir();
			}
		}
	}
}
