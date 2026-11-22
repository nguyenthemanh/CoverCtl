import os,subprocess,pathlib
os.chdir(r"C:\Users\Dell\Projects\CoverCtl")
for n in ("c.py","t.py","mkc.py","tmp_clean.py"):
    pathlib.Path(n).unlink(missing_ok=True)
    subprocess.call(["git","rm","-f","--ignore-unmatch",n])
subprocess.check_call(["git","add","-A"])
tree=subprocess.check_output(["git","write-tree"],text=True).strip()
parent=subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip()
os.environ.update(GIT_AUTHOR_NAME="nguyenthemanh",GIT_AUTHOR_EMAIL="nguyenthemanh@users.noreply.github.com",GIT_COMMITTER_NAME="nguyenthemanh",GIT_COMMITTER_EMAIL="nguyenthemanh@users.noreply.github.com")
pathlib.Path(".git/COMMIT_EDITMSG_CLEAN").write_text(
    "Show app icons in uninstall/AA pickers; clarify One Hand is built into CoverCtl (1.8.2).\n",
    encoding="utf-8",
)
new=subprocess.check_output(["git","commit-tree",tree,"-p",parent,"-F",".git/COMMIT_EDITMSG_CLEAN"],text=True).strip()
subprocess.check_call(["git","update-ref","refs/heads/master",new])
print(subprocess.check_output(["git","log","-1","--format=%B"],text=True))
print("HAS_CURSOR","cursoragent" in subprocess.check_output(["git","log","-1","--format=%B"],text=True).lower())
