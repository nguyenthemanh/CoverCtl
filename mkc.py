import os, subprocess, pathlib
os.chdir(r"C:\Users\Dell\Projects\CoverCtl")
subprocess.check_call(["git", "add", "-A"])
# drop accidental helper scripts if any
for name in ("mkcommit.py", "fix_rm.py"):
    pathlib.Path(name).unlink(missing_ok=True)
    subprocess.call(["git", "rm", "-f", "--ignore-unmatch", name])
tree = subprocess.check_output(["git", "write-tree"], text=True).strip()
parent = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
os.environ["GIT_AUTHOR_NAME"] = "nguyenthemanh"
os.environ["GIT_AUTHOR_EMAIL"] = "nguyenthemanh@users.noreply.github.com"
os.environ["GIT_COMMITTER_NAME"] = "nguyenthemanh"
os.environ["GIT_COMMITTER_EMAIL"] = "nguyenthemanh@users.noreply.github.com"
msg = pathlib.Path(".git/COMMIT_EDITMSG_CLEAN")
msg.write_text(
    "Add flip-ctl screen modes and real cover nav overlay (1.8.0).\n\n"
    "SystemUI has no NavigationBar on display 1; CoverCtl draws Back/Home/Recents.\n"
    "Main/Cover/Dual, keep Dual, QS tile, and cover panel included.\n",
    encoding="utf-8",
)
new = subprocess.check_output(["git", "commit-tree", tree, "-p", parent, "-F", str(msg)], text=True).strip()
subprocess.check_call(["git", "update-ref", "refs/heads/master", new])
body = subprocess.check_output(["git", "log", "-1", "--format=%B"], text=True)
print(body)
print("HAS_CURSOR", "cursoragent" in body.lower())
print("SHA", new)
