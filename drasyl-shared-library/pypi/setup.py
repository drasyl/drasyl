import setuptools
import os
from lxml import etree as ET

try:
    # read version from pom.xml
    pom = ET.parse('../pom.xml', parser=ET.XMLParser(remove_comments=False))
    version = pom.find("./{*}parent/{*}version").text
    version = version.replace("-SNAPSHOT", ".dev0")
except:
    version = os.path.basename(os.path.dirname(os.path.realpath(__file__)))

# binary package
try:
    from wheel.bdist_wheel import bdist_wheel as _bdist_wheel
    class bdist_wheel(_bdist_wheel):
        def finalize_options(self):
            _bdist_wheel.finalize_options(self)
            self.root_is_pure = False
except ImportError:
    bdist_wheel = None

class BinaryDistribution (setuptools.Distribution):
    def has_ext_modules(self):
        return True

setuptools.setup(
    cmdclass = { 'bdist_wheel': bdist_wheel },
    package_data = { '': ['*'] },
    distclass = BinaryDistribution,
    version = version
)
