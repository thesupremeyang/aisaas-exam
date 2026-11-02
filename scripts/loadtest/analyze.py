#!/usr/bin/env python3
"""
解析压测 .jtl（JMeter 同格式），按接口给出分位数、错误分布与发起窗口。

用法：python3 analyze.py run-100.jtl [run-800.jtl ...]

相对理论考试系统版本的三处增强：
  1. §5.1 延迟只统计成功样本
     连接被拒/快速失败是毫秒级返回的，混进分位数会把 p50 压得很低，
     看起来「很快」，实际是「一半的人根本没成功」。所以分位数只对 success==true 算。
  2. 增加「发起窗口」
     验证集合点是否真的生效：同一批请求的发起时间戳跨度应该极短（亚秒级）。
     如果跨度被拉到几秒，说明请求被客户端排队了，测出来的不是真实并发。
  3. 增加吞吐 req/s
     与天花板对比，判断还有多少余量。

看什么：
  - p95/p99 比平均值重要：考生的真实体验由长尾决定
  - 错误按「接口 + 业务码」分组，并抽样打印原始报错
  - 错误时间分布：集中在第一秒 = 瞬时尖峰（要削峰）；
    均匀散布 = 持续过载（要扩容或优化单次耗时）
"""
import csv
import re
import sys
from collections import Counter, defaultdict

PERCENTILES = (50, 90, 95, 99)
# 18 位数字 id 归一，避免同一接口被拆成上千个分组
ID_RE = re.compile(r'/\d{15,}')


def pct(values, p):
    if not values:
        return 0
    ordered = sorted(values)
    idx = min(len(ordered) - 1, max(0, round((p / 100) * (len(ordered) - 1))))
    return ordered[idx]


def endpoint_of(row):
    label = (row.get('label') or '').strip()
    if label:
        return label
    url = (row.get('URL') or '').strip()
    if not url:
        return '(未知)'
    path = url.split('//', 1)[-1].split('/', 1)[-1] if '//' in url else url
    return ID_RE.sub('/{id}', '/' + path).split('?')[0]


def report(path):
    with open(path, encoding='utf-8', newline='') as fh:
        rows = list(csv.DictReader(fh))
    if not rows:
        print(f'{path}: 空结果')
        return

    by_label = defaultdict(list)
    errors = Counter()
    error_samples = {}
    total_err = 0
    t0 = min(int(float(r.get('timeStamp', 0) or 0)) for r in rows)
    t_end = max(int(float(r.get('timeStamp', 0) or 0)) + int(float(r.get('elapsed', 0) or 0))
                for r in rows)
    err_offsets = []

    for r in rows:
        ep = endpoint_of(r)
        ok = r.get('success', 'false') == 'true'
        by_label[ep].append((int(float(r.get('elapsed', 0) or 0)),
                             ok,
                             int(float(r.get('timeStamp', 0) or 0))))
        if not ok:
            total_err += 1
            key = (ep, r.get('responseCode', '?'))
            errors[key] += 1
            error_samples.setdefault(key, (r.get('responseMessage', '') or '')[:160])
            err_offsets.append((int(float(r.get('timeStamp', 0) or 0)) - t0) / 1000.0)

    span = (t_end - t0) / 1000.0
    print(f'\n===== {path} =====')
    print(f'  总请求 {len(rows)}  失败 {total_err}  错误率 {total_err / len(rows) * 100:.2f}%'
          f'  全程 {span:.1f}s  整体吞吐 {len(rows) / span:.0f} req/s')

    print(f'  {"接口":<14}{"次数":>6}{"p50":>8}{"p90":>8}{"p95":>8}{"p99":>8}'
          f'{"最大":>8}{"错误率":>9}{"发起窗口":>10}')
    for ep, items in sorted(by_label.items()):
        # §5.1：分位数只对成功样本计算
        ok_times = [t for t, ok, _ in items if ok]
        err = sum(1 for _, ok, _ in items if not ok)
        starts = [ts for _, _, ts in items]
        window = (max(starts) - min(starts)) / 1000.0
        if ok_times:
            cells = ''.join(f'{pct(ok_times, p):>8}' for p in PERCENTILES) + f'{max(ok_times):>8}'
        else:
            cells = ''.join(f'{"-":>8}' for _ in PERCENTILES) + f'{"-":>8}'
        print(f'  {ep:<14}{len(items):>6}' + cells +
              f'{err / len(items) * 100:>8.1f}%{window:>9.2f}s')

    if errors:
        print('  --- 错误分布 ---')
        for (ep, code), n in errors.most_common():
            print(f'  {ep} → 业务码 {code} × {n}')
            print(f'      样例：{error_samples[(ep, code)]}')
        if err_offsets:
            err_offsets.sort()
            print('  --- 错误时间分布（相对首个请求，秒）---')
            print(f'      最早 {err_offsets[0]:.1f}s  最晚 {err_offsets[-1]:.1f}s  '
                  f'中位 {pct([int(x * 1000) for x in err_offsets], 50) / 1000:.1f}s')
            buckets = Counter(int(o // 2) * 2 for o in err_offsets)
            print('      每 2 秒一档：' + '  '.join(f'{b}s:{n}' for b, n in sorted(buckets.items())))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    for p in sys.argv[1:]:
        report(p)
