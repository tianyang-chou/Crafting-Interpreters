package com.craftinginterpreters.lox;
import java.util.List;
import java.util.ArrayList;

class Interpreter implements Expr.Visitor<Object>, 
							 Stmt.Visitor<Void> { // Interpreter is concrete visitor
	final Environment globals = new Environment();
	private Environment environment = globals;

	Interpreter() {
		globals.define("clock", new LoxCallable() {
			@Override
			public int arity() { return 0; }

			@Override
			public Object call(Interpreter interpreter,
							   List<Object> arguments) {
				return (double)System.currentTimeMillis() / 1000.0;
			}

			@Override
			public String toString() { return "<native fn>"; }
		});
	}

	void interpret(List<Stmt> statements) {
		try {
			for (Stmt stmt : statements) {
			    execute(stmt);
			}
		} catch (RuntimeError error) {
			Lox.runtimeError(error);
		}
	}

	private String stringify(Object object) {
		if (object == null) return "nil";

		if (object instanceof Double) {
			String text = object.toString();
			if (text.endsWith(".0")) {
				text = text.substring(0, text.length() - 2);
			}
			return text;
		}

		return object.toString();
	}

	Object evaluate(Expr expr) {
		return expr.accept(this);
	}

	private void execute (Stmt stmt) {
		stmt.accept(this);
	}

	@Override
	public Void visitBlockStmt(Stmt.Block stmt) {
		executeBlock(stmt.statements, new Environment(environment));
		return null;
	}

	void executeBlock(List<Stmt> statements, Environment environment) {
		Environment previous = this.environment;
		try {
			this.environment = environment;

			for (Stmt statement : statements) {
				execute(statement);
			}
		} finally {
			this.environment = previous;
		}
	}

	@Override
	public Object visitCallExpr(Expr.Call expr) {
		Object callee = evaluate(expr.callee);

		List<Object> arguments = new ArrayList<>();
		for (Expr argument : expr.arguments) {
			arguments.add(evaluate(argument));
		}

		if (!(callee instanceof LoxCallable)) {
			throw new RuntimeError(expr.paren, 
					"Can only call functions and classes.");
		}

		LoxCallable function = (LoxCallable)callee;
		if (arguments.size () != function.arity()) {
			throw new RuntimeError(expr.paren, "Expected " +
					function.arity() + " arguments but got " +
					arguments.size() + ".");
		}

		return function.call(this, arguments);
	}

	@Override
	public Void visitIfStmt(Stmt.If stmt) {
		if (isTruthy(evaluate(stmt.condition))) {
			execute(stmt.thenBranch);
		} else if (stmt.elseBranch != null) {
			execute(stmt.elseBranch);
		}
		return null;
	}

	@Override 
	public Void visitWhileStmt(Stmt.While stmt) {
		while (isTruthy(evaluate(stmt.condition))) {
			execute(stmt.body);
		}
		return null;
	}

	@Override 
	public Void visitVarStmt(Stmt.Var stmt) {
		Object value = null;
		if (stmt.initializer != null) {
			value = evaluate(stmt.initializer);
		}

		if (value != null){
			environment.define(stmt.name.lexeme, value);
		}
		return null;
	}

	@Override
	public Object visitAssignExpr(Expr.Assign expr) {
		environment.get(expr.name);
		Object value = evaluate(expr.expression);
		environment.assign(expr.name, value);
		return value;
	}

	@Override
	public Void visitExpressionStmt(Stmt.Expression stmt) {
		Object value = evaluate(stmt.expression);
		System.out.println(stringify(value));
		return null;
	}

	@Override
	public Void visitFunctionStmt(Stmt.Function stmt) {
		LoxFunction function = new LoxFunction(stmt, environment);
		environment.define(stmt.name.lexeme, function);
		return null;
	}

	@Override
	public Void visitPrintStmt(Stmt.Print stmt) {
		Object value = evaluate(stmt.expression);
		System.out.println(stringify(value));
		return null;
	}

	@Override
	public Void visitReturnStmt(Stmt.Return stmt) {
		Object value = null;
		if(stmt.value != null) value = evaluate(stmt.value);

		throw new Return(value);
	}

	@Override
	public Object visitVariableExpr(Expr.Variable expr) {
		return environment.get(expr.name);
	}

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
		return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
		return evaluate(expr.expression);
	}

	@Override
	public Object visitConditionalExpr(Expr.Conditional expr) {
		Object result = evaluate(expr.expr);

		if (isTruthy(result)){
			return evaluate(expr.thenBranch);
		} else{
			return evaluate(expr.elseBranch);
		}
	}

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

		switch (expr.operator.type) {
			case MINUS:
				checkNumberOperand(expr.operator, right);
				return -(double)right;
			case BANG:
				return !isTruthy(right);
			default:
				return null;
		}
    }

	public void checkNumberOperand(Token operator, Object operand) {
		if (operand instanceof Double) return;
		throw new RuntimeError(operator, "Operand must be a number.");
	}

	public void checkNumberOperands(Token operator, Object left, Object right) {
		if (left instanceof Double && right instanceof Double) return;
		throw new RuntimeError(operator, "Operands must be numbers.");
	}

	/* Lox follows Ruby’s simple rule: 
	 * false and nil are falsey, and 
	 * everything else is truthy. */
	private boolean isTruthy(Object object) {
		if (object == null) return false;
		if (object instanceof Boolean) return (boolean)object;
		return true;
	}

	private boolean isEqual(Object a, Object b) {
		if (a == null && b == null) return true;
		if (a == null) return false;

		return a.equals(b);
	}

	@Override
	public Object visitLogicalExpr(Expr.Logical expr) {
		Object left = evaluate(expr.left);
		
		if(expr.operator.type == TokenType.OR) {
			if (isTruthy(left)) return left;
		} else {
			if (!isTruthy(left)) return left;
		}

		return evaluate(expr.right);
	}		

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
		Object result;

		switch(expr.operator.type){
			case GREATER:
				checkNumberOperands(expr.operator, left, right);
				return (double)left > (double)right;
			case LESS:
				checkNumberOperands(expr.operator, left, right);
				return (double)left < (double)right;
			case GREATER_EQUAL:
				checkNumberOperands(expr.operator, left, right);
				return (double)left >= (double)right;
			case LESS_EQUAL:
				checkNumberOperands(expr.operator, left, right);
				return (double)left <= (double)right;
			case PLUS:
				if (left instanceof Double && right instanceof Double){ 
					return (double)left + (double)right;
				}
				if (left instanceof String && right instanceof String){
					return (String)left + (String)right;
				}
				if (left instanceof String && right instanceof Double){
				    String text = right.toString();
					if (text.endsWith(".0")) {
						text = text.substring(0, text.length() - 2);
					}
				    return (String)left + text; 
				}
				if (left instanceof Double && right instanceof String){
					String text = left.toString();
					if (text.endsWith(".0")) {
						text = text.substring(0, text.length() - 2);
					}
				    return text + (String)right;
				}
				throw new RuntimeError(expr.operator, 
						"Operands must be two numbers or two strings.");
			case MINUS:
				checkNumberOperands(expr.operator, left, right);
				return (double)left - (double)right;
			case STAR:
				checkNumberOperands(expr.operator, left, right);
				return (double)left * (double)right;
			case SLASH:
				checkNumberOperands(expr.operator, left, right);
				if ((Double)right == 0) {
					throw new RuntimeError(expr.operator, 
							"Divide a number by zero is prohibited.");
				}
				return (double)left / (double)right;
			case BANG_EQUAL: 
				checkNumberOperands(expr.operator, left, right);
				return !isEqual(left, right);
			case EQUAL_EQUAL: 
				checkNumberOperands(expr.operator, left, right);
				return isEqual(left, right);
			default:
				return null;
		}
	}
}
