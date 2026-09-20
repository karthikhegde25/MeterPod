package com.karthikhegde.meterpod.calculator

/**
 * A small hand-rolled expression evaluator so the calculator can parse a
 * full typed expression like "3+4*sin(30)^2" rather than only working one
 * operation at a time.
 *
 * Grammar (highest to lowest precedence):
 *   primary    := NUMBER | IDENT ['(' expression ')'] | '(' expression ')'
 *   postfix    := primary ('!' | '%')*
 *   unary      := ('-' | '+') unary | postfix
 *   power      := unary ('^' power)?         (right-associative)
 *   term       := power (('*' | '/') power)*
 *   expression := term (('+' | '-') term)*
 */
class ExpressionEvaluator(private val angleInDegrees: Boolean) {

    private sealed class Token {
        data class Number(val value: Double) : Token()
        data class Ident(val name: String) : Token()
        data class Symbol(val c: Char) : Token()
    }

    fun evaluate(expression: String): Double {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")
        val parser = Parser(tokens)
        val result = parser.parseExpression()
        if (!parser.isAtEnd()) throw IllegalArgumentException("Unexpected trailing input")
        return result
    }

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    tokens.add(Token.Number(input.substring(start, i).toDouble()))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < input.length && input[i].isLetter()) i++
                    tokens.add(Token.Ident(input.substring(start, i)))
                }
                c in "+-*/^()!%" -> {
                    tokens.add(Token.Symbol(c))
                    i++
                }
                else -> i++ // skip anything unrecognized rather than hard-failing on stray input
            }
        }
        return tokens
    }

    private inner class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun isAtEnd() = pos >= tokens.size
        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun advance(): Token = tokens[pos++]
        private fun isSymbol(token: Token?, c: Char) = token is Token.Symbol && token.c == c

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                val tok = peek()
                when {
                    isSymbol(tok, '+') -> { advance(); value += parseTerm() }
                    isSymbol(tok, '-') -> { advance(); value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (true) {
                val tok = peek()
                when {
                    isSymbol(tok, '*') -> { advance(); value *= parsePower() }
                    isSymbol(tok, '/') -> { advance(); value /= parsePower() }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            return if (isSymbol(peek(), '^')) {
                advance()
                Math.pow(base, parsePower()) // right-associative
            } else {
                base
            }
        }

        private fun parseUnary(): Double {
            val tok = peek()
            if (isSymbol(tok, '-')) { advance(); return -parseUnary() }
            if (isSymbol(tok, '+')) { advance(); return parseUnary() }
            return parsePostfix()
        }

        private fun parsePostfix(): Double {
            var value = parsePrimary()
            while (true) {
                val tok = peek()
                when {
                    isSymbol(tok, '!') -> { advance(); value = factorial(value) }
                    isSymbol(tok, '%') -> { advance(); value /= 100.0 }
                    else -> return value
                }
            }
        }

        private fun parsePrimary(): Double {
            val tok = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
            return when (tok) {
                is Token.Number -> { advance(); tok.value }
                is Token.Symbol -> {
                    if (tok.c == '(') {
                        advance()
                        val value = parseExpression()
                        if (!isSymbol(peek(), ')')) throw IllegalArgumentException("Expected ')'")
                        advance()
                        value
                    } else {
                        throw IllegalArgumentException("Unexpected symbol ${tok.c}")
                    }
                }
                is Token.Ident -> {
                    advance()
                    when (tok.name.lowercase()) {
                        "pi" -> Math.PI
                        "e" -> Math.E
                        else -> parseFunctionCall(tok.name.lowercase())
                    }
                }
            }
        }

        private fun parseFunctionCall(name: String): Double {
            if (!isSymbol(peek(), '(')) throw IllegalArgumentException("Expected '(' after $name")
            advance()
            val arg = parseExpression()
            if (!isSymbol(peek(), ')')) throw IllegalArgumentException("Expected ')'")
            advance()
            return applyFunction(name, arg)
        }

        private fun applyFunction(name: String, arg: Double): Double {
            val argInRadians = if (angleInDegrees) Math.toRadians(arg) else arg
            return when (name) {
                "sin" -> kotlin.math.sin(argInRadians)
                "cos" -> kotlin.math.cos(argInRadians)
                "tan" -> kotlin.math.tan(argInRadians)
                "asin" -> maybeToDegrees(kotlin.math.asin(arg))
                "acos" -> maybeToDegrees(kotlin.math.acos(arg))
                "atan" -> maybeToDegrees(kotlin.math.atan(arg))
                "sqrt" -> kotlin.math.sqrt(arg)
                "log" -> kotlin.math.log10(arg)
                "ln" -> kotlin.math.ln(arg)
                "abs" -> kotlin.math.abs(arg)
                "exp" -> kotlin.math.exp(arg)
                else -> throw IllegalArgumentException("Unknown function '$name'")
            }
        }

        private fun maybeToDegrees(radians: Double): Double = if (angleInDegrees) Math.toDegrees(radians) else radians

        private fun factorial(value: Double): Double {
            if (value < 0 || kotlin.math.abs(value - Math.round(value).toDouble()) > 1e-9) {
                throw IllegalArgumentException("Factorial needs a non-negative whole number")
            }
            val n = Math.round(value).toInt()
            if (n > 170) throw IllegalArgumentException("Factorial too large to represent")
            var result = 1.0
            for (i in 2..n) result *= i
            return result
        }
    }
}
