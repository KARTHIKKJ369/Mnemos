package encrypteduploads

import("fmt";"io";"os";"path/filepath")
// Reader exposes ordered encrypted chunks as one seekable opaque ciphertext stream.
type Reader struct{root string; chunks []Chunk; offsets []int64; size,pos int64; current *os.File; currentIndex int}
func NewReader(root string,chunks []Chunk)(*Reader,error){r:=&Reader{root:root,chunks:chunks,currentIndex:-1};r.offsets=make([]int64,len(chunks));for i,c:=range chunks{if c.Index!=i||c.Size<0{return nil,fmt.Errorf("corrupt encrypted chunk map")};r.offsets[i]=r.size;r.size+=c.Size};return r,nil}
func(r *Reader)Close()error{if r.current!=nil{return r.current.Close()};return nil}
func(r *Reader)Read(p []byte)(int,error){if r.pos>=r.size{return 0,io.EOF};total:=0;for len(p)>0&&r.pos<r.size{i:=r.chunkAt(r.pos);if err:=r.open(i);err!=nil{return total,err};off:=r.pos-r.offsets[i];if _,err:=r.current.Seek(off,io.SeekStart);err!=nil{return total,err};max:=r.chunks[i].Size-off;if int64(len(p))>max{p=p[:max]};n,err:=r.current.Read(p);r.pos+=int64(n);total+=n;p=p[n:];if err!=nil&&err!=io.EOF{return total,err};if n==0{return total,io.ErrUnexpectedEOF}};return total,nil}
func(r *Reader)Seek(offset int64,whence int)(int64,error){var next int64;switch whence{case io.SeekStart:next=offset;case io.SeekCurrent:next=r.pos+offset;case io.SeekEnd:next=r.size+offset;default:return 0,fmt.Errorf("invalid seek")};if next<0{return 0,fmt.Errorf("negative seek")};r.pos=next;return next,nil}
func(r *Reader)chunkAt(pos int64)int{for i:=len(r.offsets)-1;i>=0;i--{if pos>=r.offsets[i]{return i}};return 0}
func(r *Reader)open(i int)error{if r.currentIndex==i{return nil};if r.current!=nil{r.current.Close()};f,err:=os.Open(filepath.Join(r.root,filepath.FromSlash(r.chunks[i].Path)));if err!=nil{return fmt.Errorf("open encrypted chunk: %w",err)};r.current=f;r.currentIndex=i;return nil}
