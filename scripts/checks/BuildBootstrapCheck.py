"""Exercise container build wiring with disposable files and a Docker stub; never compile on the host."""
import json, os, pathlib, shutil, stat, subprocess, tempfile
SOURCE=pathlib.Path(__file__).resolve().parents[1]/"build-container.sh"
checks=0

def verify(name, ok):
    global checks
    assert ok, name
    checks+=1
    print("PASS", name)

with tempfile.TemporaryDirectory(prefix="jellylive build check ") as temporary:
    root=pathlib.Path(temporary)
    project=root/"project with spaces"
    (project/"scripts").mkdir(parents=True)
    (project/"app/src/main/assets").mkdir(parents=True)
    (project/".local-build/bootstrap").mkdir(parents=True)
    shutil.copyfile(SOURCE,project/"scripts/build-container.sh")
    shutil.copyfile(SOURCE.parents[1]/".gitignore",project/".gitignore")
    subprocess.run(['git','init','-q',str(project)],check=True)
    (project/"gradle.properties").write_text("jellyfin.version=v0.1.1\n")
    tools=root/"tools";tools.mkdir()
    docker=tools/"docker"
    docker.write_text("""#!/usr/bin/env python3
import json,os,pathlib,sys
args=sys.argv[1:]
pathlib.Path(os.environ['BUILD_CHECK_LOG']).write_text(json.dumps(args))
if os.environ.get('BUILD_CHECK_FAIL')=='1':sys.exit(7)
mounts=[args[i+1] for i,x in enumerate(args) if x=='--mount']
source=next(x for x in mounts if ',dst=/src' in x).split(',src=',1)[1].split(',dst=',1)[0]
apk=pathlib.Path(source)/'app/build/outputs/apk/debug/jellyfin-androidtv-v0.1.1-debug.apk'
apk.parent.mkdir(parents=True,exist_ok=True);apk.write_bytes(b'fixture APK, not installable')
""")
    docker.chmod(0o700)
    log=root/"docker-args.json"
    env={k:v for k,v in os.environ.items() if k not in ('BESTV_EMBED_CONFIG','BESTV_EMBED_LIBRARY_CONFIG','JELLYLIVE_EMBED_CONFIG','JELLYLIVE_GRADLE_CACHE')}
    env.update(PATH=str(tools)+os.pathsep+env['PATH'],BUILD_CHECK_LOG=str(log))
    def run(**extra):
        log.unlink(missing_ok=True)
        result=subprocess.run(['sh',str(project/'scripts/build-container.sh')],env=dict(env,**extra),capture_output=True,text=True)
        args=json.loads(log.read_text()) if log.exists() else []
        return result,args
    def mounts(args):return [args[i+1] for i,x in enumerate(args) if x=='--mount' and ',dst=/bootstrap/' in args[i+1]]
    plain,args=run()
    verify('no local files produces plain build',plain.returncode==0 and not mounts(args) and (project/'dist/BestvLive-v0.1.1.apk').exists())
    bootstrap=project/'.local-build/bootstrap'
    box=bootstrap/'bestv-bootstrap.json';library=bootstrap/'jellyfin-bootstrap.json'
    box.write_text('{"user_id":"fixture-live"}');library.write_text('{"address":"http://fixture.invalid","username":"fixture-user","password":"fixture-password"}')
    initial={p.name:p.read_bytes() for p in bootstrap.iterdir()}
    configured,args=run()
    verify('default local files injected read-only',configured.returncode==0 and len(mounts(args))==2 and all(x.endswith(',readonly') for x in mounts(args)))
    verify('configured flag reaches container',any('JELLYLIVE_CONFIGURED=-configured'==x for x in args))
    apk=project/'private/BestvLive-v0.1.1-configured.apk'
    verify('configured APK stays private',apk.exists() and stat.S_IMODE(apk.stat().st_mode)==0o600)
    verify('source assets never receive credentials',not list((project/'app/src/main/assets').iterdir()))
    verify('inputs unchanged',initial=={p.name:p.read_bytes() for p in bootstrap.iterdir()})
    verify('credentials not printed','fixture-password' not in configured.stdout+configured.stderr)
    plain,args=run(JELLYLIVE_EMBED_CONFIG='0')
    verify('plain mode excludes present local files',plain.returncode==0 and not mounts(args) and 'JELLYLIVE_CONFIGURED=' in args)
    override=root/'custom library.json';override.write_text(library.read_text())
    override_result,args=run(BESTV_EMBED_LIBRARY_CONFIG=str(override))
    verify('explicit file overrides default',override_result.returncode==0 and any('src='+str(override)+',dst=/bootstrap/jellyfin-bootstrap.json' in x for x in mounts(args)))
    unsafe=project/'custom-library.json';unsafe.write_text(library.read_text())
    unsafe_result,args=run(BESTV_EMBED_LIBRARY_CONFIG=str(unsafe))
    verify('unignored repository input rejected',unsafe_result.returncode!=0 and not args)
    subprocess.run(['git','-C',str(project),'add','-f',str(library)],check=True)
    tracked_result,args=run()
    verify('tracked credentials rejected',tracked_result.returncode!=0 and not args)
    subprocess.run(['git','-C',str(project),'rm','--cached','-q',str(library)],check=True)
    missing,args=run(BESTV_EMBED_LIBRARY_CONFIG=str(root/'missing.json'))
    verify('missing explicit input fails before Docker',missing.returncode!=0 and not args)
    invalid,args=run(JELLYLIVE_EMBED_CONFIG='invalid')
    verify('invalid mode fails before Docker',invalid.returncode!=0 and not args)
    previous=apk.read_bytes();failed,args=run(BUILD_CHECK_FAIL='1')
    verify('failed container preserves previous successful APK',failed.returncode!=0 and apk.read_bytes()==previous)
    verify('failed build leaves no source credentials',not list((project/'app/src/main/assets').iterdir()))
    stray=project/'app/src/main/assets/jellyfin-bootstrap.json';stray.write_text(library.read_text())
    stale,args=run(JELLYLIVE_EMBED_CONFIG='0')
    verify('plain build rejects stray source credentials',stale.returncode!=0 and not args and stray.exists())
print(checks,'local bootstrap build checks passed')
