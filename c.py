import os, subprocess, pathlib
os.chdir(r"C:\Users\Dell\Projects\CoverCtl")
for n in ("mkc.py", "tmp_clean.py", "mkcommit.py"):
    pathlib.Path(n).unlink(missing_ok=True)
    subprocess.call(["git", "rm", "-f", "--ignore-unmatch", n])
subprocess.check_call(["git", "add", "-A"])
tree = subprocess.check_output(["git", "write-tree"], text=True).strip()
parent = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
os.environ["GIT_AUTHOR_NAME"] = "nguyenthemanh"
os.environ["GIT_AUTHOR_EMAIL"] = "nguyenthemanh@users.noreply.github.com"
os.environ["GIT_COMMITTER_NAME"] = "nguyenthemanh"
os.environ["GIT_COMMITTER_EMAIL"] = "nguyenthemanh@users.noreply.github.com"
msg = pathlib.Path(".git/COMMIT_EDITMSG_CLEAN")
msg.write_text(
    "Add OHO+-style cover edge gestures and Good Lock shortcuts (1.8.1).\n\n"
    "Samsung OHO+ cannot run on Flip cover; CoverCtl draws side handles for Back/Home/Recents.\n",
    encoding="utf-8",
)
new = subprocess.check_output(["git", "commit-tree", tree, "-p", parent, "-F", str(msg)], text=True).strip()
subprocess.check_call(["git", "update-ref", "refs/heads/master", new])
print(subprocess.check_output(["git", "log", "-1", "--format=%B"], text=True))
print("HAS_CURSOR", "cursoragent" in subprocess.check_output(["git", "log", "-1", "--format=%B"], text=True).lower())
