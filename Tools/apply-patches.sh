#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
patch_root="$repo_root/patches"

apply_patches() {
    local submodule_path="${patch_dir#"$patch_root"/}"
    local target_dir="$repo_root/$submodule_path"
    if [[ "$(git -C "$repo_root" ls-files --stage -- ":(literal)$submodule_path")" != 160000\ * ]]; then
        echo "Patch target is not a tracked submodule: $submodule_path" >&2
        return 1
    fi
    if [[ ! -e "$target_dir/.git" ]]; then
        echo "Initialize the submodule before applying patches: $submodule_path" >&2
        return 1
    fi

    local applied_count=${#patches[@]}
    while ((applied_count > 0)); do
        if cat "${patches[@]:0:applied_count}" | git -C "$target_dir" apply --reverse --check 2>/dev/null; then
            break
        fi
        applied_count=$((applied_count - 1))
    done
    if ((applied_count == ${#patches[@]})); then
        echo "Already applied patches: $submodule_path"
        return
    fi
    if cat "${patches[@]:applied_count}" | git -C "$target_dir" apply --check; then
        cat "${patches[@]:applied_count}" | git -C "$target_dir" apply
        echo "Applied patches: $submodule_path"
    else
        echo "Cannot apply patches; review them against the submodule: $submodule_path" >&2
        return 1
    fi
}

shopt -s globstar nullglob
patch_dir=""
patches=()
for patch in "$patch_root"/**/*.patch; do
    if [[ ! "${patch##*/}" =~ ^[0-9]{4}-[a-z0-9]+(-[a-z0-9]+)*\.patch$ ]]; then
        echo "Patch names must use NNNN-description.patch: $patch" >&2
        exit 1
    fi
    if [[ "${patch%/*}" != "$patch_dir" ]]; then
        if ((${#patches[@]} > 0)); then
            apply_patches
        fi
        patch_dir="${patch%/*}"
        patches=()
    fi
    patches+=("$patch")
done
if ((${#patches[@]} > 0)); then
    apply_patches
fi
