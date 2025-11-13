#!/usr/bin/env python3
"""
GitHub Issue Validation Agent
Validates incoming issues for completeness, duplicates, and documentation coverage.
"""

import os
import json
import subprocess
import tempfile
from pathlib import Path
from github import Github

class IssueValidationAgent:
    def __init__(self):
        self.github_token = os.environ['GITHUB_TOKEN']
        self.copilot_pat = os.environ['COPILOT_PAT']
        self.issue_number = os.environ['ISSUE_NUMBER']
        self.issue_title = os.environ['ISSUE_TITLE']
        self.issue_body = os.environ.get('ISSUE_BODY', '')
        self.issue_author = os.environ['ISSUE_AUTHOR']
        self.repository = os.environ['REPOSITORY']
        
        self.gh = Github(self.github_token)
        self.repo = self.gh.get_repo(self.repository)
        self.issue = self.repo.get_issue(int(self.issue_number))
        
        self.temp_dir = Path(tempfile.mkdtemp())
        
    def run_copilot(self, prompt, context_files=None):
        """Execute copilot CLI with given prompt and optional context files."""
        cmd = ['copilot', '-p', prompt]
        
        if context_files:
            for file in context_files:
                if Path(file).exists():
                    cmd.extend(['-f', file])
        
        env = os.environ.copy()
        env['GITHUB_TOKEN'] = self.copilot_pat
        
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                env=env,
                timeout=120
            )
            return result.stdout
        except subprocess.TimeoutExpired:
            print("Copilot command timed out")
            return None
        except Exception as e:
            print(f"Error running copilot: {e}")
            return None
    
    def step1_summarize_issue(self):
        """Generate and save issue summary."""
        print(f"Step 1: Summarizing issue #{self.issue_number}")
        
        issue_content = f"""# Issue #{self.issue_number}: {self.issue_title}
**Author:** {self.issue_author}

## Description
{self.issue_body}
"""
        
        issue_file = self.temp_dir / "current_issue.md"
        issue_file.write_text(issue_content)
        
        prompt = f"""Analyze this GitHub issue and create a structured summary including:
- Issue type (bug, feature request, question, documentation)
- Key problem statement
- Expected vs actual behavior (if applicable)
- Environment details mentioned
- Reproduction steps (if provided)
- Relevant code snippets or error messages

Format the response as a markdown document."""
        
        summary = self.run_copilot(prompt, [str(issue_file)])
        
        if summary:
            summary_file = self.temp_dir / "issue_summary.md"
            summary_file.write_text(summary)
            print(f"Summary saved to {summary_file}")
            return str(summary_file)
        return None
    
    def step2_validate_completeness(self, summary_file):
        """Validate that required fields are properly populated."""
        print("Step 2: Validating issue completeness")
        
        requirements_file = Path("ProperIssueRequirements.md")
        if not requirements_file.exists():
            print("Warning: ProperIssueRequirements.md not found, skipping validation")
            return {"valid": True, "reason": "No requirements file"}
        
        prompt = f"""Review this issue against the requirements in ProperIssueRequirements.md.

Respond with ONLY a JSON object (no markdown formatting) with this structure:
{{
  "valid": true/false,
  "missing_fields": ["field1", "field2"],
  "invalid_fields": ["field1", "field2"],
  "suggestions": "Specific guidance for what's missing"
}}"""
        
        result = self.run_copilot(prompt, [summary_file, "ProperIssueRequirements.md"])
        
        if result:
            try:
                # Extract JSON from potential markdown code blocks
                result = result.strip()
                if result.startswith("```"):
                    lines = result.split("\n")
                    result = "\n".join(lines[1:-1]) if len(lines) > 2 else result
                
                validation = json.loads(result)
                
                if not validation.get("valid", True):
                    self.post_validation_comment(validation)
                    self.issue.add_to_labels("needs-more-information")
                    print("Issue validation failed - comment posted and label applied")
                else:
                    print("Issue validation passed")
                
                return validation
            except json.JSONDecodeError as e:
                print(f"Failed to parse validation result as JSON: {e}")
                print(f"Raw result: {result}")
                return {"valid": True, "reason": "Parse error"}
        
        return {"valid": True, "reason": "No result"}
    
    def post_validation_comment(self, validation):
        """Post a comment requesting missing information."""
        missing = validation.get("missing_fields", [])
        invalid = validation.get("invalid_fields", [])
        suggestions = validation.get("suggestions", "Please provide more details.")
        
        comment = f"""Thank you for submitting this issue! 

To help us address this effectively, we need some additional information:
"""
        
        if missing:
            comment += f"""
**Missing Information:**
{chr(10).join(f"- {field}" for field in missing)}
"""
        
        if invalid:
            comment += f"""
**Invalid Information:**
{chr(10).join(f"- {field}" for field in invalid)}
"""
        
        comment += f"""
**Guidance:**
{suggestions}

Please update your issue with these details. We'll review it again once you've provided the requested information."""
        
        self.issue.create_comment(comment)
    
    def step3_check_documentation(self, summary_file):
        """Check if issue is covered in documentation or wiki."""
        print("Step 3: Checking documentation coverage")
        
        # Download wiki as markdown files
        wiki_dir = self.temp_dir / "wiki"
        wiki_files = []
        
        try:
            wiki_dir.mkdir(exist_ok=True)
            
            # Clone the wiki repository
            wiki_url = f"https://github.com/{self.repository}.wiki.git"
            print(f"Cloning wiki from {wiki_url}")
            
            result = subprocess.run(
                ["git", "clone", wiki_url, str(wiki_dir)],
                capture_output=True,
                text=True,
                timeout=60
            )
            
            if result.returncode == 0:
                # Get all markdown files from wiki
                wiki_files = list(wiki_dir.glob("*.md"))
                print(f"Downloaded {len(wiki_files)} wiki pages")
            else:
                print(f"Could not clone wiki: {result.stderr}")
        except subprocess.TimeoutExpired:
            print("Wiki clone timed out")
        except FileNotFoundError:
            print("Git not found, skipping wiki download")
        except Exception as e:
            print(f"Could not access wiki: {e}")
        
        # Gather context files: summary, wiki pages, and README files
        readme_files = list(Path(".").glob("**/README.md"))
        context_files = [str(summary_file)] + [str(f) for f in wiki_files] + [str(f) for f in readme_files[:5]]
        
        print(f"Analyzing with {len(context_files)} context files")
        
        prompt = """Analyze if this issue is addressed in the available documentation.

Respond with ONLY a JSON object (no markdown formatting):
{
  "documented": true/false,
  "documentation_location": "path or section",
  "is_user_error": true/false,
  "is_known_issue": true/false,
  "debugging_steps": ["step1", "step2"],
  "workarounds": ["workaround1"]
}"""
        
        result = self.run_copilot(prompt, context_files)
        
        if result:
            try:
                result = result.strip()
                if result.startswith("```"):
                    lines = result.split("\n")
                    result = "\n".join(lines[1:-1]) if len(lines) > 2 else result
                
                analysis = json.loads(result)
                print(f"Documentation analysis: {json.dumps(analysis, indent=2)}")
                
                if analysis.get("documented") or analysis.get("is_user_error"):
                    self.post_documentation_comment(analysis)
                
                return analysis
            except json.JSONDecodeError as e:
                print(f"Failed to parse documentation analysis: {e}")
        
        return {}
    
    def post_documentation_comment(self, analysis):
        """Post a comment with documentation links or debugging steps."""
        if analysis.get("documented"):
            location = analysis.get("documentation_location", "the documentation")
            comment = f"""This appears to be documented in {location}. Please review the documentation and let us know if you need additional clarification."""
        elif analysis.get("is_user_error"):
            steps = analysis.get("debugging_steps", [])
            steps_text = "\n".join(f"{i+1}. {step}" for i, step in enumerate(steps))
            comment = f"""Based on the issue description, here are some debugging steps to try:

{steps_text}

Please try these steps and report back with your findings."""
        else:
            return
        
        if analysis.get("workarounds"):
            workarounds = "\n".join(f"- {w}" for w in analysis["workarounds"])
            comment += f"\n\n**Potential workarounds:**\n{workarounds}"
        
        self.issue.create_comment(comment)
    
    def step4_compile_issue_history(self):
        """Fetch all issues and compile into JSON."""
        print("Step 4: Compiling issue history")
        
        issues_data = []
        
        # Fetch recent issues (limit to avoid rate limits)
        for issue in self.repo.get_issues(state='all', sort='updated', direction='desc')[:200]:
            if issue.number == int(self.issue_number):
                continue
                
            issues_data.append({
                "number": issue.number,
                "title": issue.title,
                "body": issue.body[:500] if issue.body else "",  # Truncate for size
                "state": issue.state,
                "labels": [label.name for label in issue.labels],
                "created_at": issue.created_at.isoformat()
            })
        
        issues_file = self.temp_dir / "issues_history.json"
        issues_file.write_text(json.dumps(issues_data, indent=2))
        print(f"Compiled {len(issues_data)} issues to {issues_file}")
        
        return str(issues_file)
    
    def step5_check_duplicates(self, summary_file, issues_file):
        """Check for duplicate or related issues."""
        print("Step 5: Checking for duplicates and related issues")
        
        prompt = """Compare this issue against the historical issues to identify:

Respond with ONLY a JSON object (no markdown formatting):
{
  "is_duplicate": true/false,
  "duplicate_of": [issue_numbers],
  "is_regression": true/false,
  "regression_info": "details if applicable",
  "related_issues": [issue_numbers],
  "needs_upgrade": true/false,
  "upgrade_version": "version if applicable"
}"""
        
        result = self.run_copilot(prompt, [summary_file, issues_file])
        
        if result:
            try:
                result = result.strip()
                if result.startswith("```"):
                    lines = result.split("\n")
                    result = "\n".join(lines[1:-1]) if len(lines) > 2 else result
                
                analysis = json.loads(result)
                print(f"Duplicate analysis: {json.dumps(analysis, indent=2)}")
                
                if analysis.get("is_duplicate"):
                    self.post_duplicate_comment(analysis)
                elif analysis.get("related_issues"):
                    self.post_related_issues_comment(analysis)
                
                return analysis
            except json.JSONDecodeError as e:
                print(f"Failed to parse duplicate analysis: {e}")
        
        return {}
    
    def post_duplicate_comment(self, analysis):
        """Post comment about duplicate issues."""
        duplicates = analysis.get("duplicate_of", [])
        if duplicates:
            links = ", ".join(f"#{num}" for num in duplicates)
            comment = f"""This issue appears to be a duplicate of {links}.

Please review the linked issue(s). If your situation is different, please provide additional details that distinguish your case."""
            
            self.issue.create_comment(comment)
            self.issue.add_to_labels("duplicate")
    
    def post_related_issues_comment(self, analysis):
        """Post comment about related issues."""
        related = analysis.get("related_issues", [])
        needs_upgrade = analysis.get("needs_upgrade", False)
        
        if related:
            links = ", ".join(f"#{num}" for num in related[:5])
            comment = f"""This issue may be related to: {links}

Please review these issues to see if they provide helpful context or solutions."""
            
            if needs_upgrade:
                version = analysis.get("upgrade_version", "the latest version")
                comment += f"\n\n**Note:** This issue may be resolved in {version}. Consider upgrading if possible."
            
            self.issue.create_comment(comment)
    
    def run(self):
        """Execute the full validation workflow."""
        print(f"Starting validation for issue #{self.issue_number}: {self.issue_title}")
        
        try:
            # Step 1: Summarize
            summary_file = self.step1_summarize_issue()
            if not summary_file:
                print("Failed to generate summary, aborting")
                return
            
            # Step 2: Validate completeness
            validation = self.step2_validate_completeness(summary_file)
            if not validation.get("valid", True):
                print("Issue incomplete, validation workflow stopped")
                return
            
            # Step 3: Check documentation
            self.step3_check_documentation(summary_file)
            
            # Step 4: Compile issue history
            issues_file = self.step4_compile_issue_history()
            
            # Step 5: Check for duplicates
            self.step5_check_duplicates(summary_file, issues_file)
            
            print("Validation workflow completed successfully")
            
        except Exception as e:
            print(f"Error during validation workflow: {e}")
            raise
        finally:
            # Cleanup temp directory
            import shutil
            shutil.rmtree(self.temp_dir, ignore_errors=True)

if __name__ == "__main__":
    agent = IssueValidationAgent()
    agent.run()
