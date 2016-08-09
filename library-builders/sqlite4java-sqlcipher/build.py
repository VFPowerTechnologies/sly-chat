#!/usr/bin/env python3
#requires: patch, gant, bash
#TODO task help
#TODO improve logging
#unsupported archs: android-x86_64, android-arm64-v8a (the openssl setenv script needs to be modified to support this)
import subprocess
import shutil
from urllib import request
from os import environ, unlink
from os.path import exists, join

from tasks import Task
from utils import (make_dirs, write_to_file, get_staticlib_name_for_arch,
                   unpack_source, get_template, arch_to_setenv_info,
                   arch_is_android, get_android_configure_host_type,
                   apply_patch, get_android_abis, get_sha256_checksum)


DOWNLOAD_URLS = {
    #https://www.openssl.org/source/
    'openssl': 'https://www.openssl.org/source/openssl-1.0.2h.tar.gz',
    'sqlcipher': 'https://github.com/sqlcipher/sqlcipher/archive/v3.4.0.tar.gz',
    'sqlite4java': 'https://bitbucket.org/almworks/sqlite4java/get/fa4bb0fe7319a5f1afe008284146ac83e027de60.tar.gz',
}


#SHA256 hashes
DOWNLOAD_HASHES = {
    'openssl': '1d4007e53aad94a5b2002fe045ee7bb0b3d98f1a47f8b2bc851dcd1c74332919',
    'sqlcipher': '99b702ecf796de02bf7b7b35de4ceef145f0d62b4467a86707c2d59beea243d0',
    'sqlite4java': '24accb1c7abd9549bb28f85b35d519c87406a1dabc832772f85f6c787584f7d2',
}


class CreateWorkDirsTask(Task):
    "Creates the top-level work directories."

    def __init__(self):
        super().__init__('create-work-dirs', 'Create top-level work directories')

    def run(self, task_context):
        for dir in [task_context['root-src-path'],
                    task_context['root-build-path'],
                    task_context['root-prefix-path'],
                    task_context['root-output-path'],
                    task_context['root-android-output-path']]:
            make_dirs(dir)


class CreateArchDirsTask(Task):
    "Creates subdirectories for each build arch."

    def __init__(self):
        super().__init__('create-arch-dirs', 'Create build and prefix subdirectories for each arch')

        self.add_dependency('create-work-dirs')

    def run(self, task_context):
        for root in [task_context['root-prefix-path'], task_context['root-build-path']]:
            for arch in task_context['archs']:
                path = join(root, arch)
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
    "Generic per-arch build Task base."

    def __init__(self, task_name, build_item_name, lib_name):
        super().__init__(task_name, 'Build ' + build_item_name)
        self.build_item_name = build_item_name
        self.lib_name = lib_name

        self.add_dependency('create-arch-dirs')

    def do_build(self, text_context, arch, prefix_dir, build_dir):
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

        for arch in task_context['archs']:
            prefix_dir = join(task_context['root-prefix-path'], arch)

            lib_path = join(prefix_dir, 'lib', get_staticlib_name_for_arch(arch, self.lib_name))
            if exists(lib_path):
                print('%s lib present, skipping build' % self.lib_name)
                continue

            print('Building for ' + arch)
            build_dir = join(task_context['root-build-path'], arch, self.build_item_name)
            unpack_source(src_path, build_dir)

            self.do_build(task_context, arch, prefix_dir, build_dir)

            print('Verifying build output')
            if not exists(lib_path):
                raise RuntimeError('OpenSSL build failed, unable to find lib at %s' % lib_path)


class BuildOpenSSLTask(BuildTask):
    def __init__(self):
        super().__init__('build-openssl', 'openssl', 'crypto')
        self.add_dependency('download-openssl')

    def _get_linux_template(self, prefix_dir):
        template = get_template('openssl-linux-build.sh')
        context = {
            'prefix': prefix_dir,
        }
        return template.substitute(**context)

    def _get_android_template(self, task_context, prefix_dir, arch):
        template = get_template('openssl-android-build.sh')
        context = {
            'prefix': prefix_dir,
            'sdk-root': task_context['android-sdk-home'],
            'ndk-root': task_context['android-ndk-home'],
        }
        return template.substitute(**context)

    def _write_setenv_android(self, build_dir, arch):
        template = get_template('setenv-android.sh')
        aarch, eabi = arch_to_setenv_info(arch)
        context = {
            #see NDK_HOME/toolchains/
            #x86-4.9, arm-linux-androideabi-4.6, etc
            'eabi': eabi,
            #x86 or arm (nothing else supported by script)
            'arch': aarch,
            'api': '19',
        }
        write_to_file(join(build_dir, 'setenv-android.sh'), template.substitute(**context))

    def do_build(self, task_context, arch, prefix_dir, build_dir):
        if arch_is_android(arch):
            self._write_setenv_android(build_dir, arch)
            template = self._get_android_template(task_context, prefix_dir, arch)
        else:
            template = self._get_linux_template(prefix_dir)

        self.run_build_script(build_dir, template)


class BuildSQLCipher(BuildTask):
    def __init__(self):
        super().__init__('build-sqlcipher', 'sqlcipher', 'sqlcipher')
        self.add_dependency('build-openssl')
        self.add_dependency('download-sqlcipher')

    def _get_android_template(self, task_context, prefix_dir, arch):
        template = get_template('sqlcipher-android-build.sh')
        aarch, eabi = arch_to_setenv_info(arch)
        context = {
            'prefix': prefix_dir,
            'ndk-home': task_context['android-ndk-home'],
            'api': '19',
            'eabi': eabi,
            'host': get_android_configure_host_type(arch),
            #arm/mips/x86
            'arch': aarch,
            #FIXME
            #this is the building system's arch
            'host-arch': 'linux-x86_64',
        }
        return template.substitute(**context)


    def _get_linux_template(self, prefix_dir):
        template = get_template('sqlcipher-linux-build.sh')
        context = {
            'prefix': prefix_dir,
        }
        return template.substitute(**context)

    def do_build(self, task_context, arch, prefix_dir, build_dir):
        if arch_is_android(arch):
            template = self._get_android_template(task_context, prefix_dir, arch)
        else:
            template = self._get_linux_template(prefix_dir)

        #need to copy more recent config.sub/guess scripts
        #FIXME find libtool dir or pass in as setting
        libtool_dir = '/usr/share/libtool/build-aux'
        for ext in ['sub', 'guess']:
            shutil.copy(join(libtool_dir, 'config.' + ext), build_dir)

        self.run_build_script(build_dir, template)


class BuildSQLite4JavaTask(Task):
    def __init__(self):
        super().__init__('build-sqlite4java', 'Build sqlite4java')

        self.add_dependency('build-sqlcipher')
        self.add_dependency('download-sqlite4java')

    def _apply_linux_patch(self, task_context, prefix_dir, build_dir):
        prefix_dir = join(task_context['root-prefix-path'], 'linux-x86_64')
        print('Applying linux patch')
        patch_context = {
            'prefix': prefix_dir,
        }
        apply_patch(build_dir, 'sqlite4java-linux', patch_context)

    def _apply_android_patch(self, android_abis, root_prefix_dir, build_dir):
        print('Applying android patch')
        patch_context = {
            'root_prefix': root_prefix_dir,
            'abi_list': ' '.join(android_abis),
        }
        apply_patch(build_dir, 'sqlite4java-android', patch_context)

    #TODO this code is a mess right now
    def run(self, task_context):
        build_linux_x86_64 = 'linux-x86_64' in task_context['archs']

        android_abis = get_android_abis(task_context['archs'])

        src_path = join(task_context['root-src-path'], 'sqlite4java.tar.gz')
        #we can build for every arch using the same src setup
        build_dir = join(task_context['root-build-path'], 'sqlite4java')
        unpack_source(src_path, build_dir)

        gant_targets = []

        if build_linux_x86_64:
            so_path = join(task_context['root-output-path'], 'libsqlite4java-linux-amd64.so')
            if not exists(so_path):
                prefix_dir = join(task_context['root-prefix-path'], 'linux-x86_64')
                self._apply_linux_patch(task_context, prefix_dir, build_dir)
                gant_targets.append('lib.link')
            else:
                print('linux-x86_64 already present, not building')
                build_linux_x86_64 = False

        if len(android_abis) > 0:
            new_abis = []
            for abi in android_abis:
                so_path = join(task_context['root-android-output-path'], abi, 'libsqlite4java-android.so')
                if not exists(so_path):
                    new_abis.append(abi)
                else:
                    print('%s already present, not building' % abi)

            android_abis = new_abis
            if len(android_abis) > 0:
                self._apply_android_patch(android_abis, task_context['root-prefix-path'], build_dir)
                gant_targets.append('sqlcipher-android')

        if len(gant_targets) <= 0:
            print('Nothing to build')
            return

        argv = [
            #FIXME
            '/home/kevin/apps/gant/bin/gant',
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

        if build_linux_x86_64:
            #output is in BUILD_DIR/build/lib.release.linux-amd64/libsqlite4java-linux-amd64.so
            so_path = join(build_dir, 'build', 'lib.release.linux-amd64', 'libsqlite4java-linux-amd64.so')
            shutil.move(so_path, task_context['root-output-path'])


def create_download_task(key):
    "Returns a task to download the specific url. The task name will be `download-<key>`."
    return DownloadTask(key, DOWNLOAD_URLS[key], DOWNLOAD_HASHES[key], '%s.tar.gz' % key)


def add_download_tasks(tasks):
    "Adds all required download tasks to the given Tasks."

    for key in DOWNLOAD_URLS.keys():
        tasks.add(create_download_task(key))
