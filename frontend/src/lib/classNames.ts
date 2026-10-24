/** 接收类名及可忽略的假值；返回过滤后以空格连接的类名字符串。 */
export function classNames(...values: (string | false | null | undefined)[]) {
  return values.filter(Boolean).join(' ')
}
