/**
 * @author      : zhoualec (ztyemail@126.com)
 * @file        : Lox
 * @created     : Monday Dec 23, 2024 11:43:13 CST
 */

package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox
{
	static boolean hadError = false;
	public static void main(String[] args) throws IOException {
		if (args.length > 1) {
			System.out.println("Usage: Jlox [script]");
			System.exit(64);
		} else if (args.length == 1) {
			runFile(args[0]);
		} else {
			runPrompt();
		}
	}
	
	public static void runFile(String path) throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get(path));
		run(new String(bytes, Charset.defaultCharset()));

		if (hadError) System.exit(65);
	}

	public static void runPrompt() throws IOException {
		InputStreamReader input = new InputStreamReader(System.in);
		BufferedReader reader = new BufferedReader(input);

		for(;;) {
			System.out.printf("> ");
			String line = reader.readLine();
			if (line == null) break;
			run(line);
			
			// reset error flag after each process of single line of code
			hadError = false;
		}
	}

	private static void run(String source) {
		Scanner scanner = new Scanner(source);
		List<Token> tokens = scanner.scanTokens();

		Parser parser = new Parser(tokens);
		Expr expr = parser.parse();

		// Stop if there was a syntax error.
		if (hadError) return;

		System.out.println(new AstPrinter().print(expr));

		// For now, just print the tokens
		// for (Token token : tokens) {
		//     System.out.println(token);
		// }
	}

	static void error(int line, String message) {
		report(line, "", message);
	}

	private static void report(int line, String where, String message) {
		System.err.println(
				"[line " + line + "] Error" + where + ": " + message);
		hadError = true;
	}

	static void error(Token token, String message) {
		if (token.type == TokenType.EOF) {
			report(token.line, " at end", message);
		} else {
			report(token.line, " at '" + token.lexeme + "'", message);
		}
	}
}


