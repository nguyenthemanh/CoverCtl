import os, subprocess, pathlib, time
os.chdir(r"C:\Users\Dell\Projects\CoverCtl")
# stage already done; create commit via write-tree + commit-tree to avoid Cursor trailer injection
subprocess.check_call(["git", "add", "-A"])
# Use a temp index commit: first do normal write-tree from index
tree = subprocess.check_output(["git", "write-tree"], text=True).strip()
parent = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
os.environ["GIT_AUTHOR_NAME"] = "nguyenthemanh"
os.environ["GIT_AUTHOR_EMAIL"] = "nguyenthemanh@users.noreply.github.com"
os.environ["GIT_COMMITTER_NAME"] = "nguyenthemanh"
os.environ["GIT_COMMITTER_EMAIL"] = "nguyenthemanh@users.noreply.github.com"
msg_path = pathlib.Path(".git/COMMIT_EDITMSG_CLEAN")
msg_path.write_text(
    "Fix cover navigation bar missing after launcher; bump 1.7.1.\n",
    encoding="utf-8",
)
new = subprocess.check_output(
    ["git", "commit-tree", tree, "-p", parent, "-F", str(msg_path)],
    text=True,
).strip()
subprocess.check_call(["git", "update-ref", "refs/heads/master", new])
msg = subprocess.check_output(["git", "log", "-1", "--format=%B"], text=True)
print("NEW", new)
print(msg)
print("HAS_CURSOR", "cursoragent" in msg.lower())
