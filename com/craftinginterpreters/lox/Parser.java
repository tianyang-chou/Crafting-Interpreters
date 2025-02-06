/*
 * Productions of expression: 
 * ------------------------------------------------------------
 * expression     → assignment ;
 * assignment     → IDENTIFIER "=" assignment | conditional;
 * conditional    → comma ( "?" expression ":" conditional )? ;
 * comma          → equality ( "," equality)* ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
  	              | primary ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
                  | "(" expression ")" | expression "?" expression ":" expression 
				  | IDENTIFIER ;;
 * ------------------------------------------------------------
 * Productions of statement:
 * ------------------------------------------------------------
 * program       → declaration* EOF ; 
 * declaration   → varDecl | statement ; // A declaration includes declaring or non-declaring statement
 * varDecl       → "var" IDENTIFIER ( "=" expression )? ";" ;
 * statement     → exprStmt | printStmt | block ;
 * block         → "{" declaration* "}" ;
 * exprStmt      → expression ";" ;
 * printStmt     → "print" expression ";" ;
 * ------------------------------------------------------------
 * Productions of variable declaration:
 * ------------------------------------------------------------
 * ------------------------------------------------------------
*/
package com.craftinginterpreters.lox;

import java.util.List;
import java.util.ArrayList;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
	private static class ParseError extends RuntimeException {}

	private final List<Token> tokens;
	private int current = 0;

	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}

	List<Stmt> parse() {
		List<Stmt> statements = new ArrayList<>();
		while (!isAtEnd()) {
			statements.add(declaration());
		}

		return statements;
	}

	private Stmt declaration() {
		try {
			if (match(VAR)) return varDeclaration();

			return statement();
		} catch (ParseError error) {
			synchronize();
			return null;
		}
	}

	private Stmt varDeclaration() {
		Token name = consume(IDENTIFIER, "Expect a variable name.");

		Expr initializer = null;
		if (match(EQUAL)) {
			initializer = expression();
		}

		consume(SEMICOLON, "Expect ';' after variable declaration.");
		return new Stmt.Var(name, initializer);
	}

	private Stmt statement() {
		if (match(PRINT)) return printStatement();
		if (match(LEFT_BRACE)) return new Stmt.Block(block());
		return expressionStatement();
	}		
	
	private List<Stmt> block() {
		List<Stmt> statements = new ArrayList<>();

		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			statements.add(declaration());
		}

		consume(RIGHT_BRACE, "Expect '}' after block.");
		return statements;
	}

	private Stmt printStatement() {
		Expr value = expression();
		consume(SEMICOLON, "Expect ';' after value.");
		return new Stmt.Print(value);
	}

	private Stmt expressionStatement() {
		Expr expr = expression();
		consume(SEMICOLON, "Expect ';' after expression.");
		return new Stmt.Expression(expr);
	}

	private Expr expression() {
		return assignment();
	}

	private Expr assignment() {
		Expr expr = conditional(); 

		if(match(EQUAL)) {
			Token equals = previous();
			Expr value = assignment();

			if (expr instanceof Expr.Variable) {
				Token name = ((Expr.Variable)expr).name;
				return new Expr.Assign(name, value);
			}

			error(equals, "Invalid assignment target.");
		}

		return expr;
	}

	private Expr conditional() {
		Expr expr = comma();
	    
		while(match(QUESTION)) {
			Expr thenBranch = expression();
			consume(COLON, "Expect ':' after then branch of conditional expression.");
			Expr elseBranch = conditional();
			expr = new Expr.Conditional(expr, thenBranch, elseBranch);	
		}

		return expr;
	}

	private Expr comma() {
		Expr expr = equality();
	    
		while(match(COMMA)) {
			Token operator = previous();
			Expr right = equality();
			expr = new Expr.Binary(expr, operator, right);	
		}

		return expr;
	}

	private Expr equality(){
		Expr expr = comparison();

		while(match(BANG_EQUAL, EQUAL_EQUAL)) {
			Token operator = previous();
			Expr right = comparison();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	private Expr comparison() {
		Expr expr = term();

		while(match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			Token operator = previous();
			Expr right = term();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	private Expr term() {
		Expr expr = factor();

		while(match(PLUS, MINUS)) {
			Token operator = previous();
			Expr right = factor();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	private Expr factor() {
		Expr expr = unary();

		while(match(SLASH, STAR)) {
			Token operator = previous();
			Expr right = unary();
			expr = new Expr.Binary(expr, operator, right);
		}

		return expr;
	}

	private Expr unary() {
		if(match(BANG, MINUS)){
			Token operator = previous();
			Expr right = unary();
			return new Expr.Unary(operator, right);
		}

		return primary();
	}

	private Expr primary() {
		if(match(FALSE)) return new Expr.Literal(false);
		if(match(TRUE)) return new Expr.Literal(true);
		if(match(NIL)) return new Expr.Literal(null);

		if(match(NUMBER, STRING)) {
			return new Expr.Literal(previous().literal);
		}

		if(match(LEFT_PAREN)) {
			Expr expr = expression();
			consume(RIGHT_PAREN, "Expect ')' after expression.");
			return new Expr.Grouping(expr);
		}

		if(match(IDENTIFIER)) {
			return new Expr.Variable(previous());
		}

		Expr left = expression();

		throw error(peek(), "Expect expression.");
	}
	
	private boolean match(TokenType... types) {
		for (TokenType type: types){
			if(check(type)){
				advance();
				return true;
			}
		}
		return false;
	}

	private Token consume(TokenType type, String message) {
		if(check(type)) return advance();

		throw error(peek(), message);
	}

	private ParseError error(Token token, String message) {
		Lox.error(token, message);
		return new ParseError();
	}

	/* discards tokens until it thinks it has 
	 * found a statement boundary. After catching 
	 * a ParseError 
	*/
	private void synchronize() {
		advance();

		while (!isAtEnd()) {
			if (previous().type == SEMICOLON) return;

			switch (peek().type) {
				case CLASS:
				case FUN:
				case VAR:
				case IF:
				case WHILE:
				case PRINT:
				case RETURN:
					return;
			}

			advance();
		}
	}

	private boolean check(TokenType type) {
		if (isAtEnd()) return false;
		return peek().type == type;
	}

	private Token advance() {
		if (!isAtEnd()) current++;
		return previous();
	}

	private boolean isAtEnd() {
		return peek().type == EOF;
	}

	private Token previous() {
		return tokens.get(current-1);
	}

	private Token peek() {
		return tokens.get(current);
	}
}
