from setuptools import setup, Distribution
from wheel.bdist_wheel import bdist_wheel

class DrasylWheel(bdist_wheel):
    def finalize_options(self):
        bdist_wheel.finalize_options(self)
        self.root_is_pure = False

class DrasylDistribution(Distribution):
    def __init__(self, *attrs):
        Distribution.__init__(self, *attrs)
        self.cmdclass['bdist_wheel'] = DrasylWheel

    def has_ext_modules(self):
        return True

setup(
    # force platform specific package wheel
    distclass = DrasylDistribution,

    # Here we specify all the shared libs for the different platforms, but
    # setup will probably only find the one library downloaded by the build
    # script or placed here manually.
    package_data = {
        'drasyl': ['*.so*', '*.dll', '*.dylib']
    },
)
