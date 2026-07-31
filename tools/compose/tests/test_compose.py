import copy,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validate_compose import validate,resolved,ROOT
class ComposeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.valid=resolved(); cls.script=(ROOT/"ops/db/init-databases.sh").read_text()
        cls.override=(ROOT/"ops/compose/testing/compose.s10.yml").read_text()
    def test_valid(self): validate(copy.deepcopy(self.valid))
    def assert_mutant(self,fn):
        d=copy.deepcopy(self.valid); fn(d)
        with self.assertRaises(ValueError): validate(d)
    def test_independent_structural_mutants(self):
        mutations=[
          ("extra service",lambda d:d["services"].update(other=copy.deepcopy(d["services"]["gateway"]))),
          ("published backend",lambda d:d["services"]["backend"].update(ports=[{"published":"8080","target":8080}])),
          ("wildcard gateway",lambda d:d["services"]["gateway"]["ports"][0].update(host_ip="0.0.0.0")),
          ("mutable image",lambda d:d["services"]["backend"].update(image="backend:latest")),
          ("build",lambda d:d["services"]["backend"].update(build=".")),
          ("privileged",lambda d:d["services"]["backend"].update(privileged=True)),
          ("host network",lambda d:d["services"]["backend"].update(network_mode="host")),
          ("no init",lambda d:d["services"]["backend"].update(init=False)),
          ("restart",lambda d:d["services"]["backend"].update(restart="always")),
          ("stop",lambda d:d["services"]["backend"].update(stop_grace_period="5s")),
          ("security",lambda d:d["services"]["backend"].update(security_opt=[])),
          ("logs",lambda d:d["services"]["backend"]["logging"]["options"].update({"max-size":"1g"})),
          ("resources",lambda d:d["services"]["backend"].pop("mem_limit")),
          ("network member",lambda d:d["services"]["frontend"]["networks"].update({"emporio-db":None})),
          ("db not internal",lambda d:d["networks"]["emporio-db"].update(internal=False)),
          ("mount",lambda d:d["services"]["backend"].update(volumes=[])),
          ("health",lambda d:d["services"]["backend"]["healthcheck"].update(test=["CMD","true"])),
          ("depends",lambda d:d["services"]["backend"].update(depends_on={})),
          ("env leak",lambda d:d["services"]["frontend"]["environment"].update(DB_PASSWORD="leak")),
          ("backend env leak",lambda d:d["services"]["backend"]["environment"].update(UNAUTHORIZED="leak")),
          ("docker socket",lambda d:d["services"]["backend"].update(volumes=[{"type":"bind","source":"/var/run/docker.sock","target":"/var/run/docker.sock"}])),
          ("gateway writable",lambda d:d["services"]["gateway"].update(read_only=False)),
          ("wrong ERP url",lambda d:d["services"]["website_back"]["environment"].update(ERP_API_URL="http://legacy:8080")),
          ("smtp health drift",lambda d:d["services"]["backend"]["environment"].update(MANAGEMENT_HEALTH_MAIL_ENABLED="true")),
          ("missing website sync alias",lambda d:d["services"]["backend"]["environment"].pop("WEBSITE_SYNC_API_KEY")),
          ("divergent sync aliases",lambda d:d["services"]["backend"]["environment"].update(WEBSITE_SYNC_API_KEY="different")),
          ("missing website backend sync alias",lambda d:d["services"]["website_back"]["environment"].pop("WEBSITE_ERP_SYNC_KEY")),
          ("backend Flyway enabled",lambda d:d["services"]["backend"]["environment"].update(SPRING_FLYWAY_ENABLED="true")),
          ("backend Flyway missing",lambda d:d["services"]["backend"]["environment"].pop("SPRING_FLYWAY_ENABLED")),
          ("website Flyway enabled",lambda d:d["services"]["website_back"]["environment"].update(FLYWAY_ENABLED="true")),
          ("website Flyway missing",lambda d:d["services"]["website_back"]["environment"].pop("FLYWAY_ENABLED")),
          ("whatsapp published",lambda d:d["services"]["whatsapp_service"].update(ports=[{"published":"3001","target":3001}])),
        ]
        for label,fn in mutations:
            with self.subTest(label=label): self.assert_mutant(fn)
    def test_database_script_mutants(self):
        for old,new in (("ON_ERROR_STOP=1","ON_ERROR_STOP=0"),("rolcanlogin","rolsuper"),
          ("pg_get_userbyid(datdba)","datname"),("PASSWORD %L","PASSWORD %s")):
            with self.subTest(old=old),self.assertRaises(ValueError):
                validate(copy.deepcopy(self.valid),self.script.replace(old,new),self.override)
    def test_override_mutants(self):
        for old,new in (("backend:s10","backend:s08"),("https://erp.s10.invalid","https://erp-emporio.abaronesa.net.br"),
          ("WHATSAPP_INITIALIZATION_DISABLED","REMOVED_DISABLED")):
            with self.subTest(old=old),self.assertRaises(ValueError):
                validate(copy.deepcopy(self.valid),self.script,self.override.replace(old,new))
if __name__=="__main__": unittest.main()
