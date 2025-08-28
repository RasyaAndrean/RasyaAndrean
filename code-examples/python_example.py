#!/usr/bin/env python3
"""
AI-Powered Code Review Assistant
Author: Rasya Andrean
Description: Advanced code analysis using machine learning and natural language processing
"""

import ast
import re
import json
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from pathlib import Path
import tensorflow as tf
from transformers import AutoTokenizer, AutoModel
import numpy as np

@dataclass
class CodeIssue:
    """Represents a code issue found during analysis"""
    severity: str  # 'low', 'medium', 'high', 'critical'
    category: str  # 'security', 'performance', 'style', 'bug'
    line_number: int
    description: str
    suggestion: str
    confidence: float

class AICodeReviewer:
    """AI-powered code review system using transformer models"""

    def __init__(self, model_path: str = "microsoft/codebert-base"):
        self.tokenizer = AutoTokenizer.from_pretrained(model_path)
        self.model = AutoModel.from_pretrained(model_path)
        self.security_patterns = self._load_security_patterns()
        self.performance_patterns = self._load_performance_patterns()

    def _load_security_patterns(self) -> List[Dict[str, Any]]:
        """Load security vulnerability patterns"""
        return [
            {
                'pattern': r'eval\s*\(',
                'severity': 'critical',
                'description': 'Use of eval() can lead to code injection',
                'suggestion': 'Use ast.literal_eval() for safe evaluation',
                'confidence': 0.95
            },
            {
                'pattern': r'exec\s*\(',
                'severity': 'critical',
                'description': 'Use of exec() can execute arbitrary code',
                'suggestion': 'Avoid exec() or use restricted execution environment',
                'confidence': 0.95
            },
            {
                'pattern': r'pickle\.loads?\s*\(',
                'severity': 'high',
                'description': 'Pickle deserialization can execute arbitrary code',
                'suggestion': 'Use json or other safe serialization formats',
                'confidence': 0.90
            },
            {
                'pattern': r'(subprocess|os)\.(system|popen|check_output)\s*\(',
                'severity': 'high',
                'description': 'Shell command execution without proper sanitization',
                'suggestion': 'Use subprocess.run() with shell=False and proper input validation',
                'confidence': 0.85
            },
            {
                'pattern': r'input\s*\(',
                'severity': 'medium',
                'description': 'Direct user input without validation',
                'suggestion': 'Validate and sanitize all user inputs',
                'confidence': 0.80
            },
            {
                'pattern': r'Crypto\.Random\.random\(\)',
                'severity': 'high',
                'description': 'Use of insecure random number generator',
                'suggestion': 'Use secrets module for cryptographically secure random numbers',
                'confidence': 0.90
            },
            {
                'pattern': r'(md5|sha1)\.update\(',
                'severity': 'high',
                'description': 'Use of weak cryptographic hash functions',
                'suggestion': 'Use SHA-256 or stronger hash functions',
                'confidence': 0.85
            },
            {
                'pattern': r'password\s*=\s*.*',
                'severity': 'medium',
                'description': 'Potential hardcoded password',
                'suggestion': 'Use environment variables or secure configuration management',
                'confidence': 0.70
            }
        ]

    def _load_performance_patterns(self) -> List[Dict[str, Any]]:
        """Load performance anti-patterns"""
        return [
            {
                'pattern': r'for\s+\w+\s+in\s+range\s*\(\s*len\s*\(',
                'severity': 'medium',
                'description': 'Inefficient iteration pattern',
                'suggestion': 'Use enumerate() or iterate directly over the sequence',
                'confidence': 0.80
            },
            {
                'pattern': r'\.append\s*\(\s*\)\s*in\s+for',
                'severity': 'low',
                'description': 'List comprehension might be more efficient',
                'suggestion': 'Consider using list comprehension',
                'confidence': 0.70
            },
            {
                'pattern': r'import\s+(\w+).*\n.*\1\.\w+\(.*\)\s*\*\s*1000',
                'severity': 'medium',
                'description': 'Inefficient repeated function calls',
                'suggestion': 'Store function reference in a variable to avoid repeated attribute access',
                'confidence': 0.75
            },
            {
                'pattern': r'global\s+\w+',
                'severity': 'medium',
                'description': 'Use of global variables reduces code maintainability',
                'suggestion': 'Pass variables as parameters or use class attributes',
                'confidence': 0.80
            },
            {
                'pattern': r'with\s+open\([^)]*\)\s+as\s+\w+:\s*\w+\.read\(\)',
                'severity': 'medium',
                'description': 'Reading entire file into memory may cause memory issues',
                'suggestion': 'Process file in chunks or use streaming approach for large files',
                'confidence': 0.85
            },
            {
                'pattern': r'\.sort\(\s*key\s*=.*\)\s*\[\s*-1\s*\]',
                'severity': 'medium',
                'description': 'Inefficient way to find maximum element',
                'suggestion': 'Use max() function with key parameter instead',
                'confidence': 0.80
            },
            {
                'pattern': r'list\(.*\)\.index\(',
                'severity': 'medium',
                'description': 'Linear search with O(n) complexity',
                'suggestion': 'Use set or dict for O(1) lookup if possible',
                'confidence': 0.75
            }
        ]

    def analyze_code(self, code: str, filename: str = "unknown") -> List[CodeIssue]:
        """Perform comprehensive code analysis"""
        issues = []

        # Static analysis
        issues.extend(self._static_analysis(code))

        # Pattern-based analysis
        issues.extend(self._pattern_analysis(code))

        # AI-based analysis
        issues.extend(self._ai_analysis(code))

        # Complexity analysis
        issues.extend(self._complexity_analysis(code))

        return sorted(issues, key=lambda x: (x.severity, x.line_number))

    def _static_analysis(self, code: str) -> List[CodeIssue]:
        """Perform AST-based static analysis"""
        issues = []

        try:
            tree = ast.parse(code)

            for node in ast.walk(tree):
                # Check for potential issues
                if isinstance(node, ast.FunctionDef):
                    if len(node.args.args) > 7:
                        issues.append(CodeIssue(
                            severity='medium',
                            category='style',
                            line_number=node.lineno,
                            description=f'Function {node.name} has too many parameters ({len(node.args.args)})',
                            suggestion='Consider using a configuration object or breaking into smaller functions',
                            confidence=0.8
                        ))

                elif isinstance(node, ast.If):
                    # Check for deeply nested conditions
                    depth = self._calculate_nesting_depth(node)
                    if depth > 4:
                        issues.append(CodeIssue(
                            severity='medium',
                            category='style',
                            line_number=node.lineno,
                            description=f'Deeply nested condition (depth: {depth})',
                            suggestion='Consider extracting conditions into separate functions',
                            confidence=0.7
                        ))

        except SyntaxError as e:
            issues.append(CodeIssue(
                severity='critical',
                category='bug',
                line_number=e.lineno or 1,
                description=f'Syntax error: {e.msg}',
                suggestion='Fix syntax error',
                confidence=1.0
            ))

        return issues

    def _pattern_analysis(self, code: str) -> List[CodeIssue]:
        """Analyze code using regex patterns"""
        issues = []
        lines = code.split('\n')

        all_patterns = self.security_patterns + self.performance_patterns

        for line_num, line in enumerate(lines, 1):
            for pattern_info in all_patterns:
                if re.search(pattern_info['pattern'], line):
                    issues.append(CodeIssue(
                        severity=pattern_info['severity'],
                        category='security' if pattern_info in self.security_patterns else 'performance',
                        line_number=line_num,
                        description=pattern_info['description'],
                        suggestion=pattern_info['suggestion'],
                        confidence=pattern_info.get('confidence', 0.9)
                    ))

        return issues

    def _ai_analysis(self, code: str) -> List[CodeIssue]:
        """Use AI model for advanced code analysis"""
        issues = []

        # Tokenize code
        inputs = self.tokenizer(code, return_tensors="pt", truncation=True, max_length=512)

        # Get embeddings
        with tf.device('/CPU:0'):  # Use CPU for inference
            outputs = self.model(**inputs)
            embeddings = outputs.last_hidden_state.mean(dim=1).detach().numpy()

        # Analyze embeddings for potential issues (simplified example)
        complexity_score = np.linalg.norm(embeddings)

        # Calculate confidence based on complexity score
        confidence = min(0.95, 0.3 + float(complexity_score / 20))  # Normalize confidence

        if complexity_score > 10.0:  # Threshold for high complexity
            issues.append(CodeIssue(
                severity='medium',
                category='style',
                line_number=1,
                description='Code complexity is high based on AI analysis',
                suggestion='Consider refactoring into smaller, more focused functions',
                confidence=confidence
            ))

        return issues

    def _calculate_nesting_depth(self, node: ast.AST, depth: int = 0) -> int:
        """Calculate maximum nesting depth of AST node"""
        max_depth = depth

        for child in ast.iter_child_nodes(node):
            if isinstance(child, (ast.If, ast.For, ast.While, ast.With, ast.Try)):
                child_depth = self._calculate_nesting_depth(child, depth + 1)
                max_depth = max(max_depth, child_depth)

        return max_depth

    def _complexity_analysis(self, code: str) -> List[CodeIssue]:
        """Analyze code complexity metrics"""
        issues = []

        try:
            tree = ast.parse(code)

            # Calculate cyclomatic complexity
            for node in ast.walk(tree):
                if isinstance(node, ast.FunctionDef):
                    complexity = self._calculate_cyclomatic_complexity(node)
                    confidence = min(0.95, 0.5 + (complexity / 50))  # Confidence increases with complexity
                    if complexity > 10:
                        issues.append(CodeIssue(
                            severity='high' if complexity > 15 else 'medium',
                            category='style',
                            line_number=node.lineno,
                            description=f'Function {node.name} has high cyclomatic complexity ({complexity})',
                            suggestion='Consider refactoring into smaller functions to reduce complexity',
                            confidence=confidence
                        ))

        except SyntaxError:
            # Syntax errors are handled in _static_analysis
            pass

        return issues

    def _calculate_cyclomatic_complexity(self, node: ast.FunctionDef) -> int:
        """Calculate cyclomatic complexity of a function"""
        complexity = 1  # Base complexity

        for child in ast.walk(node):
            if isinstance(child, (ast.If, ast.For, ast.While, ast.With)):
                complexity += 1
            elif isinstance(child, ast.ExceptHandler):
                complexity += 1
            elif isinstance(child, ast.BoolOp):
                # Each additional operand in boolean operations increases complexity
                complexity += len(child.values) - 1
            elif isinstance(child, ast.Compare):
                # Each comparison operator increases complexity
                complexity += len(child.ops) - 1

        return complexity

    def generate_report(self, issues: List[CodeIssue], filename: str) -> Dict[str, Any]:
        """Generate comprehensive analysis report"""
        severity_counts = {'low': 0, 'medium': 0, 'high': 0, 'critical': 0}
        category_counts = {'security': 0, 'performance': 0, 'style': 0, 'bug': 0}

        for issue in issues:
            severity_counts[issue.severity] += 1
            category_counts[issue.category] += 1

        return {
            'filename': filename,
            'total_issues': len(issues),
            'severity_breakdown': severity_counts,
            'category_breakdown': category_counts,
            'issues': [
                {
                    'severity': issue.severity,
                    'category': issue.category,
                    'line': issue.line_number,
                    'description': issue.description,
                    'suggestion': issue.suggestion,
                    'confidence': issue.confidence
                }
                for issue in issues
            ],
            'overall_score': self._calculate_score(issues)
        }

    def _calculate_score(self, issues: List[CodeIssue]) -> float:
        """Calculate overall code quality score (0-100)"""
        if not issues:
            return 100.0

        severity_weights = {'low': 1, 'medium': 3, 'high': 7, 'critical': 15}
        total_penalty = sum(severity_weights[issue.severity] for issue in issues)

        # Normalize to 0-100 scale
        score = max(0, 100 - (total_penalty * 2))
        return round(score, 2)

def main():
    """Example usage of AI Code Reviewer"""
    reviewer = AICodeReviewer()

    # Example code to analyze - more complex example with security issues
    sample_code = '''
import subprocess
import os
import pickle
import hashlib

def complex_function(a, b, c, d, e, f, g, h):
    """Function with too many parameters and high complexity"""
    result = 0

    # Security issue - using eval
    dangerous_input = input("Enter code: ")
    eval_result = eval(dangerous_input)

    # Security issue - using pickle
    data = pickle.loads(input("Enter pickled data: "))

    # Inefficient iteration pattern
    data = [1, 2, 3, 4, 5]
    for i in range(len(data)):
        result += data[i]

    # Nested conditions increasing complexity
    if a > 0:
        if b > 0:
            if c > 0:
                if d > 0:
                    if e > 0:
                        if f > 0:
                            if g > 0:
                                if h > 0:
                                    # Complex boolean expression
                                    if (a > 1 and b > 1 and c > 1 and d > 1 and
                                        e > 1 and f > 1 and g > 1 and h > 1):
                                        result = a * b * c * d * e * f * g * h

    # Security issue - subprocess command execution
    user_cmd = input("Enter command: ")
    subprocess.system(user_cmd)

    # Weak hash function
    m = hashlib.md5()
    m.update(b"password")

    # Hardcoded password
    password = "secret123"

    return result

# Global variable (anti-pattern)
global_counter = 0

def update_counter():
    global global_counter
    global_counter += 1
    return global_counter

# Reading large file into memory
def read_large_file():
    with open("large_file.txt") as f:
        content = f.read()  # Potential memory issue
    return content

# Inefficient max finding
def find_max_value(items):
    items.sort(key=lambda x: x.value)
    return items[-1]  # Inefficient way to find maximum
    '''

    # Analyze code
    issues = reviewer.analyze_code(sample_code, "sample.py")

    # Generate report
    report = reviewer.generate_report(issues, "sample.py")

    # Print results
    print(json.dumps(report, indent=2))

    print(f"\n🔍 Code Analysis Complete!")
    print(f"📊 Overall Score: {report['overall_score']}/100")
    print(f"⚠️  Total Issues: {report['total_issues']}")

    if issues:
        print("\n🚨 Issues Found:")
        for issue in issues:
            emoji = {'low': '💡', 'medium': '⚠️', 'high': '🔥', 'critical': '💀'}
            print(f"{emoji[issue.severity]} Line {issue.line_number}: {issue.description}")
            print(f"   💡 Suggestion: {issue.suggestion}")
            print(f"   🎯 Confidence: {issue.confidence:.2f}")
            print()

if __name__ == "__main__":
    main()
