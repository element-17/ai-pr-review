import styles from './Highlight.module.scss'
/** 接收一行代码文本；返回按基础词法规则高亮的文本节点。 */
export default function Highlight({ text }: { text: string }) {
  const tokens = text.split(
    /("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`[^`]*`|\/\/.*$|#.*$|\b(?:const|let|var|function|return|if|else|async|await|throw|new|class|import|export|from|def|try|except|None|True|False|null|undefined|true|false|public|private|void|interface|type|for|while|switch|case)\b|\b\d+(?:\.\d+)?\b)/g,
  )
  return (
    <>
      {tokens.map((token, index) => {
        const type = /^['"`]/.test(token)
          ? 'string'
          : /^(\/\/|#)/.test(token)
            ? 'comment'
            : /^\d/.test(token)
              ? 'number'
              : /^(const|let|var|function|return|if|else|async|await|throw|new|class|import|export|from|def|try|except|None|True|False|null|undefined|true|false|public|private|void|interface|type|for|while|switch|case)$/.test(
                    token,
                  )
                ? 'keyword'
                : ''
        return type ? (
          <span key={index} className={styles[`syntax-${type}`]}>
            {token}
          </span>
        ) : (
          token
        )
      })}
    </>
  )
}
