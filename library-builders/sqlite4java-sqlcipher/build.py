#!/usr/bin/env python3
#requires: patch, gant, bash
#TODO task help
#TODO improve logging
#unsupported platforms: android-x86_64, android-arm64-v8a (the openssl setenv script needs to be modified to support this)
import subprocess
import shutil
from urllib import request
from os import environ, unlink
from os.path import exists, join

from tasks import Task
from utils import (make_dirs, write_to_file, get_static_lib_name_for_platform,
                   unpack_source, get_template, get_file_path,
                   arch_to_setenv_info, platform_is_android,
                   get_android_configure_host_type, apply_patch,
                   get_sha256_checksum, get_os_from_platform,
                   get_dynamic_lib_name_for_platform)


DOWNLOAD_URLS = {
    #https://www.openssl.org/source/
    'openssl': 'https://www.openssl.org/source/openssl-1.0.2h.tar.gz',
    'sqlcipher': 'https://github.com/sqlcipher/sqlcipher/archive/v3.4.0.tar.gz',
    'sqlite4java': 'https://bitbucket.org/almworks/sqlite4java/get/fa4bb0fe7319a5f1afe008284146ac83e027de60.tar.gz',
    'openssl-for-iphone': 'https://github.com/x2on/OpenSSL-for-iPhone/archive/f11efc9224c76b5c64a4d1b091743f2fd87f1435.tar.gz',
}


#SHA256 hashes
DOWNLOAD_HASHES = {
    'openssl': '1d4007e53aad94a5b2002fe045ee7bb0b3d98f1a47f8b2bc851dcd1c74332919',
    'sqlcipher': '99b702ecf796de02bf7b7b35de4ceef145f0d62b4467a86707c2d59beea243d0',
    'sqlite4java': '24accb1c7abd9549bb28f85b35d519c87406a1dabc832772f85f6c787584f7d2',
    'openssl-for-iphone': 'fc8733c5c99eb5a929bbd4d686fc9da9c6072789f53ecc741c919f71d8493af8',
}


PLATFORM_LINUX = 'linux-x86_64'
PLATFORM_OSX = 'osx-x86_64'
PLATFORM_WINDOWS = 'win32-x64'
PLATFORM_IOS = 'ios'


class CreateWorkDirsTask(Task):
    "Creates the top-level work directories."

    def __init__(self):
        super().__init__('create-work-dirs', 'Create top-level work directories')

    def run(self, task_context):
        for dir in [task_context['root-src-path'],
                    task_context['root-build-path'],
                    task_context['root-prefix-path'],
                    task_context['root-output-path'],
                    task_context['root-android-output-path'],
                    task_context['root-ios-output-path']]:
            make_dirs(dir)


class CreatePlatformDirsTask(Task):
    "Creates subdirectories for each build platform."

    def __init__(self):
        super().__init__('create-platform-dirs', 'Create build and prefix subdirectories for each platform')

        self.add_dependency('create-work-dirs')

    def run(self, task_context):
        for root in [task_context['root-prefix-path'], task_context['root-build-path']]:
            for platform in task_context['platforms']:
                path = join(root, platform)
                print('Creating directory %s' % path)
                make_dirs(path)


class DownloadTask(Task):
    "Download a file to the given path."

    def __init__(self, lib_name, url, sha256_checksum, save_filename):
        super().__init__('download-' + lib_name, 'Download ' + lib_name)
        self.lib_name = lib_name
        self.url = url
        self.sha256_checksum = sha256_checksum
        self.save_filename = save_filename

        self.add_dependency('create-work-dirs')

    def run(self, task_context):
        save_path = join(task_context['root-src-path'], self.save_filename)

        if exists(save_path):
            print('%s already exists, not downloading' % save_path)
            return

        print('Downloading %s -> %s' % (self.url, save_path))
        request.urlretrieve(self.url, save_path)
        print('Verifying checksum... ', end='', flush=True)
        got = get_sha256_checksum(save_path)
        if got != self.sha256_checksum:
            print('FAILED')
            unlink(save_path)
            raise RuntimeError('SHA256 checksum verification failed for %s: got %s but expected %s' % (self.lib_name, got, self.sha256_checksum))
        else:
            print('OK')


class BuildTask(Task):
    "Generic per-platform build Task base."

    def __init__(self, task_name, build_item_name, lib_name):
        super().__init__(task_name, 'Build ' + build_item_name)
        self.build_item_name = build_item_name
        self.lib_name = lib_name

        self.add_dependency('create-platform-dirs')

    def do_build(self, text_context, platform, prefix_dir, build_dir):
        raise NotImplementedError()

    def run_build_script(self, build_dir, template):
        script_name = 'build.sh'
        script_path = join(build_dir, script_name)
        print('Writing build script to %s' % script_path)
        write_to_file(script_path, template)

        print('Starting build')
        subprocess.check_call(['bash', script_name], cwd=build_dir)

    def run(self, task_context):
        src_path = join(task_context['root-src-path'], '%s.tar.gz' % self.build_item_name)

        for platform in task_context['platforms']:
            prefix_dir = join(task_context['root-prefix-path'], platform)

            lib_path = join(prefix_dir, 'lib', get_static_lib_name_for_platform(platform, self.lib_name))
            if exists(lib_path):
                print('%s lib present, skipping build' % self.lib_name)
                continue

            print('Building for ' + platform)
            build_dir = join(task_context['root-build-path'], platform, self.build_item_name)
            unpack_source(src_path, build_dir)

            self.do_build(task_context, platform, prefix_dir, build_dir)

            print('Verifying build output')
            if not exists(lib_path):
                raise RuntimeError('%s build failed, unable to find lib at %s' % (self.build_item_name, lib_path))


class BuildOpenSSLTaskBase(BuildTask):
    #CAST causes some text relocations to be generated so we need to disable it
    _configure_options = 'no-ssl2 no-ssl3 no-cast no-comp no-dso no-hw no-engine no-shared'

    def _get_template_filename(self, platform):
        return 'openssl-%s-build.sh' % get_os_from_platform(platform)

    def _get_template(self, platform, prefix_dir):
        template = get_template(self._get_template_filename(platform))
        context = {
            'prefix': prefix_dir,
            'configure-options': self._configure_options,
        }
        return template.substitute(**context)


class IOSBuildOpenSSLTask(BuildOpenSSLTaskBase):
    def __init__(self):
        super().__init__('build-openssl-ios', 'openssl-for-iphone', 'crypto')
        self.add_dependency('download-openssl-for-iphone')

    def do_build(self, text_context, platform, prefix_dir, build_dir):
        template = self._get_template(platform, prefix_dir)

        self.run_build_script(build_dir, template)


class BuildOpenSSLTask(BuildOpenSSLTaskBase):
    #CAST causes some text relocations to be generated so we need to disable it
    _configure_options = 'no-ssl2 no-ssl3 no-cast no-comp no-dso no-hw no-engine no-shared'

    def __init__(self):
        super().__init__('build-openssl', 'openssl', 'crypto')
        self.add_dependency('download-openssl')

    def _get_android_template(self, task_context, prefix_dir, platform):
        template = get_template('openssl-android-build.sh')
        context = {
            'prefix': prefix_dir,
            'sdk-root': task_context['android-sdk-home'],
            'ndk-root': task_context['android-ndk-home'],
        }
        return template.substitute(**context)

    def _write_setenv_android(self, build_dir, platform):
        template = get_template('setenv-android.sh')
        aarch, eabi = arch_to_setenv_info(platform)
        context = {
            #see NDK_HOME/toolchains/
            #x86-4.9, arm-linux-androideabi-4.6, etc
            'eabi': eabi,
            #x86 or arm (nothing else supported by script)
            'arch': aarch,
            'api': '19',
        }
        write_to_file(join(build_dir, 'setenv-android.sh'), template.substitute(**context))

    def do_build(self, task_context, platform, prefix_dir, build_dir):
        if platform_is_android(platform):
            self._write_setenv_android(build_dir, platform)
            template = self._get_android_template(task_context, prefix_dir, platform)
        else:
            template = self._get_template(platform, prefix_dir)

        self.run_build_script(build_dir, template)


class BuildSQLCipher(BuildTask):
    def __init__(self):
        super().__init__('build-sqlcipher', 'sqlcipher', 'sqlcipher')
        self.add_dependency('download-sqlcipher')

    def configure(self, context):
        for platform in context['platforms']:
            if platform == PLATFORM_IOS:
                self.add_dependency('build-openssl-ios')
            else:
                self.add_dependency('build-openssl')

    def _get_android_template(self, task_context, prefix_dir, platform):
        template = get_template('sqlcipher-android-build.sh')
        aarch, eabi = arch_to_setenv_info(platform)
        context = {
            'prefix': prefix_dir,
            'ndk-home': task_context['android-ndk-home'],
            'api': '19',
            'eabi': eabi,
            'host': get_android_configure_host_type(platform),
            #arm/mips/x86
            'arch': aarch,
            #FIXME
            #this is the building system's platform
            'host-arch': 'linux-x86_64',
        }
        return template.substitute(**context)

    def _get_template_filename(self, platform):
        return 'sqlcipher-%s-build.sh' % get_os_from_platform(platform)

    def _get_template(self, platform, prefix_dir):
        template = get_template(self._get_template_filename(platform))
        context = {
            'prefix': prefix_dir,
        }
        return template.substitute(**context)

    def _apply_windows_patch(self, build_dir):
        print('Applying windows patch')
        apply_patch(build_dir, 'sqlcipher-win32', {})

    def do_build(self, task_context, platform, prefix_dir, build_dir):
        if platform_is_android(platform):
            template = self._get_android_template(task_context, prefix_dir, platform)
        else:
            template = self._get_template(platform, prefix_dir)

        if platform == PLATFORM_WINDOWS:
            self._apply_windows_patch(build_dir)
            subprocess.check_call(['autoreconf'], cwd=build_dir)

        if platform_is_android(platform):
            #need to copy more recent config.sub/guess scripts (for android)
            libtool_dir = join(task_context['libtool-home'], 'build-aux')
            for ext in ['sub', 'guess']:
                shutil.copy(join(libtool_dir, 'config.' + ext), build_dir)

        self.run_build_script(build_dir, template)


class BuildSQLite4JavaTask(Task):
    """
    We add two new gant targets: sqlcipher-android and sqlcipher-desktop.

    The desktop version uses build-<os>.properties files for values of cc, etc.

    Currently we only support a single architecture for each platform.
    """

    def __init__(self):
        super().__init__('build-sqlite4java', 'Build sqlite4java')

        self.add_dependency('build-sqlcipher')
        self.add_dependency('download-sqlite4java')

    def _apply_patch(self, android_abis, root_prefix_dir, build_dir, desktop_platforms):
        print('Applying patch')

        platforms = ', '.join('"%s"' % p for p in desktop_platforms)

        patch_context = {
            'root_prefix': root_prefix_dir,
            'abi_list': ' '.join(android_abis),
            'platforms': platforms
        }

        apply_patch(build_dir, 'sqlite4java', patch_context)

    def _copy_build_templates(self, build_dir, task_context, desktop_platforms):
        for platform in desktop_platforms:
            prefix_dir = join(task_context['root-prefix-path'], platform)

            template = get_template('sqlite4java-build-%s.properties' % platform)

            context = {
                'prefix': prefix_dir,
                'jdk-home': task_context['%s-jdk-home' % get_os_from_platform(platform)],
            }

            path = join(build_dir, 'ant', 'build-%s.properties' % platform)
            write_to_file(path, template.substitute(**context))

    def run(self, task_context):
        desktop_platforms = []
        android_abis = []
        build_for_ios = False

        for platform in task_context['platforms']:
            if platform_is_android(platform):
                #we just want the abi arch, so strip off the android- prefix
                abi = platform.split('-', 1)[1]

                so_path = join(task_context['root-android-output-path'], abi, 'libsqlite4java-android.so')
                if not exists(so_path):
                    android_abis.append(abi)
                else:
                    print('%s already present, not building' % abi)

            elif platform == PLATFORM_IOS:
                lib_name = 'libsqlite4java.a'

                a_path = join(task_context['root-ios-output-path'], lib_name)
                build_for_ios = not exists(a_path)
                if not build_for_ios:
                    print('%s already present, not building' % platform)

            else:
                lib_name = get_dynamic_lib_name_for_platform(platform, 'sqlite4java')

                so_path = join(task_context['root-output-path'], lib_name)
                if not exists(so_path):
                    desktop_platforms.append(platform)
                else:
                    print('%s already present, not building' % platform)

        src_path = join(task_context['root-src-path'], 'sqlite4java.tar.gz')
        #we can build for every platform using the same src setup
        build_dir = join(task_context['root-build-path'], 'sqlite4java')
        unpack_source(src_path, build_dir)

        gant_targets = []

        if len(desktop_platforms) > 0:
            gant_targets.append('sqlcipher-desktop')
            self._copy_build_templates(build_dir, task_context, desktop_platforms)

        if len(android_abis) > 0:
            gant_targets.append('sqlcipher-android')

        if build_for_ios:
            gant_targets.append('sqlcipher-ios')

        if len(gant_targets) <= 0:
            print('Nothing to build')
            return

        if build_for_ios:
            print('Copying jni.h file for IOS build')
            include_dir = join(build_dir, 'include')
            make_dirs(include_dir)
            jni_h_path = get_file_path('jni.h')
            shutil.copy(jni_h_path, include_dir)

        self._apply_patch(
            android_abis,
            task_context['root-prefix-path'],
            build_dir,
            desktop_platforms)

        argv = [
            join(task_context['gant-home'], 'bin/gant'),
            '-Dndk.home=' + task_context['android-ndk-home'],
        ]

        argv.extend(gant_targets)

        env = {
            'GROOVY_HOME': task_context['groovy-home'],
            'PATH': environ['PATH']
        }

        subprocess.check_call(argv, cwd=join(build_dir, 'ant'), env=env)

        base_lib_path = join(build_dir, 'build', 'android', 'project', 'libs')
        for abi in android_abis:
            abi_dir = join(base_lib_path, abi)
            output_path = task_context['root-android-output-path']
            print('Moving %s -> %s' % (abi_dir, output_path))
            shutil.move(abi_dir, output_path)

        for platform in desktop_platforms:
            #output is in BUILD_DIR/build/lib.release.<platform>/<lib-name>
            lib_name = get_dynamic_lib_name_for_platform(platform, 'sqlite4java')
            so_path = join(build_dir, 'build', 'lib.release.%s' % platform, lib_name)
            output_path = task_context['root-output-path']
            print('Moving %s -> %s' % (so_path, output_path))
            shutil.move(so_path, output_path)

        if build_for_ios:
            a_path = join(build_dir, 'build', 'lib.ios', 'libsqlite4java.a')
            output_path = task_context['root-ios-output-path']
            print('Moving %s -> %s' % (a_path, output_path))
            shutil.move(a_path, output_path)


def create_download_task(key):
    "Returns a task to download the specific url. The task name will be `download-<key>`."
    return DownloadTask(key, DOWNLOAD_URLS[key], DOWNLOAD_HASHES[key], '%s.tar.gz' % key)


def add_download_tasks(tasks):
    "Adds all required download tasks to the given Tasks."

    for key in DOWNLOAD_URLS.keys():
        tasks.add(create_download_task(key))
