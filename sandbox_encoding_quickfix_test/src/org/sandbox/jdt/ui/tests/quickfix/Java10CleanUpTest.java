package org.sandbox.jdt.ui.tests.quickfix;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;

public class Java10CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava10();

	enum ExplicitEncodingPatterns {

		CHARSET("""
				package test1;

				import java.io.ByteArrayOutputStream;
				import java.io.InputStreamReader;
				import java.io.FileInputStream;
				import java.io.FileReader;
				import java.io.Reader;
				import java.nio.charset.Charset;
				import java.io.FileNotFoundException;

				public class E1 {
				    void method(String filename) {
				        Charset cs1= Charset.forName("UTF-8");
				        Charset cs1b= Charset.forName("Utf-8");
				        Charset cs2= Charset.forName("UTF-16");
				        Charset cs3= Charset.forName("UTF-16BE");
				        Charset cs4= Charset.forName("UTF-16LE");
				        Charset cs5= Charset.forName("ISO-8859-1");
				        Charset cs6= Charset.forName("US-ASCII");
				        String result= cs1.toString();
				       }
				    }
				}
				""",

//					"""
//						package test1;
//
//						import java.io.ByteArrayOutputStream;
//						import java.io.InputStreamReader;
//						import java.io.FileInputStream;
//						import java.io.FileReader;
//						import java.io.Reader;
//						import java.nio.charset.Charset;
//						import java.nio.charset.StandardCharsets;
//						import java.io.FileNotFoundException;
//
//						public class E1 {
//						    void method(String filename) {
//						        Charset cs1= StandardCharsets.UTF_8;
//						        Charset cs1b= StandardCharsets.UTF_8;
//						        Charset cs2= StandardCharsets.UTF_16;
//						        Charset cs3= StandardCharsets.UTF_16BE;
//						        Charset cs4= StandardCharsets.UTF_16LE;
//						        Charset cs5= StandardCharsets.ISO_8859_1;
//						        Charset cs6= StandardCharsets.US_ASCII;
//						        String result= cs1.toString();
//						       }
//						    }
//						}
//						"""),
		"""
		package test1;

		import java.io.ByteArrayOutputStream;
		import java.io.InputStreamReader;
		import java.io.FileInputStream;
		import java.io.FileReader;
		import java.io.Reader;
		import java.nio.charset.Charset;
		import java.io.FileNotFoundException;

		public class E1 {
		    void method(String filename) {
		        Charset cs1= Charset.forName("UTF-8");
		        Charset cs1b= Charset.forName("Utf-8");
		        Charset cs2= Charset.forName("UTF-16");
		        Charset cs3= Charset.forName("UTF-16BE");
		        Charset cs4= Charset.forName("UTF-16LE");
		        Charset cs5= Charset.forName("ISO-8859-1");
		        Charset cs6= Charset.forName("US-ASCII");
		        String result= cs1.toString();
		       }
		    }
		}
		"""),
		BYTEARRAYOUTSTREAM("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        ByteArrayOutputStream ba=new ByteArrayOutputStream();
			        String result=ba.toString();
			        ByteArrayOutputStream ba2=new ByteArrayOutputStream();
			        String result2=ba2.toString("UTF-8");
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.nio.charset.StandardCharsets;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        ByteArrayOutputStream ba=new ByteArrayOutputStream();
					        String result=ba.toString(Charset.defaultCharset());
					        ByteArrayOutputStream ba2=new ByteArrayOutputStream();
					        String result2=ba2.toString(StandardCharsets.UTF_8);
					       }
					    }
					}
					"""),
		FILEREADER("""
			package test1;

			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            Reader is=new FileReader(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        try {
					            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		FILEWRITER("""
			package test1;

			import java.io.FileWriter;
			import java.io.Writer;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            Writer fw=new FileWriter(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.FileWriter;
					import java.io.OutputStreamWriter;
					import java.io.Writer;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;
					import java.io.FileOutputStream;

					public class E1 {
					    void method(String filename) {
					        try {
					            Writer fw=new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset());
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		INPUTSTREAMREADER("""
			package test1;

			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            InputStreamReader is1=new InputStreamReader(new FileInputStream("file1.txt")); //$NON-NLS-1$
			            InputStreamReader is2=new InputStreamReader(new FileInputStream("file2.txt"), "UTF-8"); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.nio.charset.StandardCharsets;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        try {
					            InputStreamReader is1=new InputStreamReader(new FileInputStream("file1.txt"), Charset.defaultCharset()); //$NON-NLS-1$
					            InputStreamReader is2=new InputStreamReader(new FileInputStream("file2.txt"), StandardCharsets.UTF_8); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		OUTPUTSTREAMWRITER("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream("")); //$NON-NLS-1$
			            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), "UTF-8"); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.nio.charset.StandardCharsets;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        try {
					            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), StandardCharsets.UTF_8); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		CHANNELSNEWREADER("""
				package test1;

				import java.io.ByteArrayOutputStream;
				import java.io.InputStreamReader;
				import java.io.FileInputStream;
				import java.io.FileReader;
				import java.io.Reader;
				import java.nio.channels.ReadableByteChannel;
				import java.nio.charset.StandardCharsets;
				import java.nio.channels.Channels;
				import java.io.FileNotFoundException;

				public class E1 {
				    void method(String filename) {
				            ReadableByteChannel ch;
				            Reader r=Channels.newReader(ch,"UTF-8"); //$NON-NLS-1$
				       }
				    }
				}
				""",

					"""
						package test1;

						import java.io.ByteArrayOutputStream;
						import java.io.InputStreamReader;
						import java.io.FileInputStream;
						import java.io.FileReader;
						import java.io.Reader;
						import java.nio.channels.ReadableByteChannel;
						import java.nio.charset.StandardCharsets;
						import java.nio.channels.Channels;
						import java.io.FileNotFoundException;

						public class E1 {
						    void method(String filename) {
						            ReadableByteChannel ch;
						            Reader r=Channels.newReader(ch,StandardCharsets.UTF_8); //$NON-NLS-1$
						       }
						    }
						}
						"""),
		CHANNELSNEWWRITER("""
				package test1;

				import java.io.ByteArrayOutputStream;
				import java.io.InputStreamReader;
				import java.io.FileInputStream;
				import java.io.FileReader;
				import java.io.Writer;
				import java.nio.channels.WritableByteChannel;
				import java.nio.charset.StandardCharsets;
				import java.nio.channels.Channels;
				import java.io.FileNotFoundException;

				public class E1 {
				    void method(String filename) {
				            WritableByteChannel ch;
				            Writer w=Channels.newWriter(ch,"UTF-8"); //$NON-NLS-1$
				       }
				    }
				}
				""",

					"""
						package test1;

						import java.io.ByteArrayOutputStream;
						import java.io.InputStreamReader;
						import java.io.FileInputStream;
						import java.io.FileReader;
						import java.io.Writer;
						import java.nio.channels.WritableByteChannel;
						import java.nio.charset.StandardCharsets;
						import java.nio.channels.Channels;
						import java.io.FileNotFoundException;

						public class E1 {
						    void method(String filename) {
						            WritableByteChannel ch;
						            Writer w=Channels.newWriter(ch,StandardCharsets.UTF_8); //$NON-NLS-1$
						       }
						    }
						}
						"""),
		PRINTWRITER("""
			package test1;

			import java.io.PrintWriter;
			import java.io.Writer;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        try {
			            Writer w=new PrintWriter(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.PrintWriter;
					import java.io.Writer;
					import java.nio.charset.Charset;
					import java.io.BufferedWriter;
					import java.io.FileNotFoundException;
					import java.io.FileOutputStream;
					import java.io.OutputStreamWriter;

					public class E1 {
					    void method(String filename) {
					        try {
					            Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset()));
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
		STRINGGETBYTES("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        String s="asdf"; //$NON-NLS-1$
			        byte[] bytes= s.getBytes();
			        byte[] bytes2= s.getBytes("UTF-8");
			        System.out.println(bytes.length);
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.nio.charset.StandardCharsets;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        String s="asdf"; //$NON-NLS-1$
					        byte[] bytes= s.getBytes(Charset.defaultCharset());
					        byte[] bytes2= s.getBytes(StandardCharsets.UTF_8);
					        System.out.println(bytes.length);
					       }
					    }
					}
					"""),
		PROPERTIESSTORETOXML("""
				package test1;

				import java.io.FileOutputStream;
				import java.io.IOException;
				import java.util.Properties;

				public class E1 {
					static void blu() throws IOException {
						Properties p=new Properties();
						try (FileOutputStream os = new FileOutputStream("out.xml")) {
							p.storeToXML(os, null,  "UTF-8");
						}
					}
				}
				""",

					"""
package test1;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class E1 {
	static void blu() throws IOException {
		Properties p=new Properties();
		try (FileOutputStream os = new FileOutputStream("out.xml")) {
			p.storeToXML(os, null,  StandardCharsets.UTF_8);
		}
	}
}
						"""),
		URLDECODER("""
package test1;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class E2 {

	static void bla() throws UnsupportedEncodingException {
		String url=URLDecoder.decode("asdf","UTF-8");
	}
}
				"""
				,
				"""
package test1;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class E2 {

	static void bla() throws UnsupportedEncodingException {
		String url=URLDecoder.decode("asdf",StandardCharsets.UTF_8);
	}
}
								"""
				),
		URLENCODER("""
				package test1;
				import java.io.UnsupportedEncodingException;
				import java.net.URLEncoder;

				public class E2 {

					static void bla() throws UnsupportedEncodingException {
						String url=URLEncoder.encode("asdf","UTF-8");
					}
				}
								"""
								,
								"""
				package test1;
				import java.io.UnsupportedEncodingException;
				import java.net.URLEncoder;
				import java.nio.charset.StandardCharsets;

				public class E2 {

					static void bla() throws UnsupportedEncodingException {
						String url=URLEncoder.encode("asdf",StandardCharsets.UTF_8);
					}
				}
												"""
								),
		SCANNER("""
package test1;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class E3 {

	static void bla() throws FileNotFoundException {
		Scanner s=new Scanner(new File("asdf"),"UTF-8");
	}
}
								"""
								,
								"""
								package test1;
								import java.io.File;
								import java.io.FileNotFoundException;
								import java.nio.charset.StandardCharsets;
								import java.util.Scanner;

								public class E3 {

									static void bla() throws FileNotFoundException {
										Scanner s=new Scanner(new File("asdf"),StandardCharsets.UTF_8);
									}
								}
												"""
								),
								FORMATTER("""
								package test1;
								import java.io.File;
								import java.io.FileNotFoundException;
								import java.io.UnsupportedEncodingException;
								import java.util.Formatter;

								public class E4 {

									static void bla() throws FileNotFoundException, UnsupportedEncodingException {
										Formatter s=new Formatter(new File("asdf"),"UTF-8");
									}
								}
								"""
								,
								"""
								package test1;
								import java.io.File;
								import java.io.FileNotFoundException;
								import java.io.UnsupportedEncodingException;
								import java.nio.charset.StandardCharsets;
								import java.util.Formatter;

								public class E4 {

									static void bla() throws FileNotFoundException, UnsupportedEncodingException {
										Formatter s=new Formatter(new File("asdf"),StandardCharsets.UTF_8);
									}
								}
												"""
								),
		THREE("""
			package test1;

			import java.io.ByteArrayOutputStream;
			import java.io.InputStreamReader;
			import java.io.FileInputStream;
			import java.io.FileReader;
			import java.io.Reader;
			import java.io.FileNotFoundException;

			public class E1 {
			    void method(String filename) {
			        String s="asdf"; //$NON-NLS-1$
			        byte[] bytes= s.getBytes();
			        System.out.println(bytes.length);
			        ByteArrayOutputStream ba=new ByteArrayOutputStream();
			        String result=ba.toString();
			        try {
			            InputStreamReader is=new InputStreamReader(new FileInputStream("")); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			        try {
			            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream("")); //$NON-NLS-1$
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			        try {
			            Reader is=new FileReader(filename);
			            } catch (FileNotFoundException e) {
			            e.printStackTrace();
			            }
			       }
			    }
			}
			""",

				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.FileInputStream;
					import java.io.FileReader;
					import java.io.Reader;
					import java.nio.charset.Charset;
					import java.io.FileNotFoundException;

					public class E1 {
					    void method(String filename) {
					        String s="asdf"; //$NON-NLS-1$
					        byte[] bytes= s.getBytes(Charset.defaultCharset());
					        System.out.println(bytes.length);
					        ByteArrayOutputStream ba=new ByteArrayOutputStream();
					        String result=ba.toString(Charset.defaultCharset());
					        try {
					            InputStreamReader is=new InputStreamReader(new FileInputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					        try {
					            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					        try {
					            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());
					            } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					       }
					    }
					}
					"""),
					ENCODINGASSTRINGPARAMETER("""
							package test1;

							import java.io.ByteArrayOutputStream;
							import java.io.InputStreamReader;
							import java.io.FileInputStream;
							import java.io.FileReader;
							import java.io.Reader;
							import java.io.FileNotFoundException;

							public class E1 {
							    void method(String filename) {
							        String s="asdf"; //$NON-NLS-1$
							        //byte[] bytes= s.getBytes(StandardCharsets.UTF_8);
							        byte[] bytes= s.getBytes("Utf-8");
							        System.out.println(bytes.length);
							        ByteArrayOutputStream ba=new ByteArrayOutputStream();
							        String result=ba.toString();
							        try {
							            InputStreamReader is=new InputStreamReader(new FileInputStream(""), "UTF-8"); //$NON-NLS-1$
							            } catch (FileNotFoundException e) {
							            e.printStackTrace();
							            }
							        try {
							            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), "UTF-8"); //$NON-NLS-1$
							            } catch (FileNotFoundException e) {
							            e.printStackTrace();
							            }
							        try {
							            Reader is=new FileReader(filename);
							            } catch (FileNotFoundException e) {
							            e.printStackTrace();
							            }
							       }
							    }
							}
							""",

								"""
									package test1;

									import java.io.ByteArrayOutputStream;
									import java.io.InputStreamReader;
									import java.io.FileInputStream;
									import java.io.FileReader;
									import java.io.Reader;
									import java.nio.charset.Charset;
									import java.nio.charset.StandardCharsets;
									import java.io.FileNotFoundException;

									public class E1 {
									    void method(String filename) {
									        String s="asdf"; //$NON-NLS-1$
									        //byte[] bytes= s.getBytes(StandardCharsets.UTF_8);
									        byte[] bytes= s.getBytes(StandardCharsets.UTF_8);
									        System.out.println(bytes.length);
									        ByteArrayOutputStream ba=new ByteArrayOutputStream();
									        String result=ba.toString(Charset.defaultCharset());
									        try {
									            InputStreamReader is=new InputStreamReader(new FileInputStream(""), StandardCharsets.UTF_8); //$NON-NLS-1$
									            } catch (FileNotFoundException e) {
									            e.printStackTrace();
									            }
									        try {
									            OutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(""), StandardCharsets.UTF_8); //$NON-NLS-1$
									            } catch (FileNotFoundException e) {
									            e.printStackTrace();
									            }
									        try {
									            Reader is=new InputStreamReader(new FileInputStream(filename), Charset.defaultCharset());
									            } catch (FileNotFoundException e) {
									            e.printStackTrace();
									            }
									       }
									    }
									}
									""");

		String given;
		String expected;

		ExplicitEncodingPatterns(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	@ParameterizedTest
	@EnumSource(ExplicitEncodingPatterns.class)
	public void testExplicitEncodingParametrized(ExplicitEncodingPatterns test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@Test
	public void testExplicitEncodingdonttouch() throws CoreException{
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E2.java",
				"""
					package test1;

					import java.io.ByteArrayOutputStream;
					import java.io.InputStreamReader;
					import java.io.IOException;
					import java.nio.charset.Charset;
					import java.io.FileInputStream;
					import java.io.FileNotFoundException;
					import java.io.UnsupportedEncodingException;

					public class E2 {
					    void method() throws UnsupportedEncodingException, IOException {
					        String s="asdf"; //$NON-NLS-1$
					        byte[] bytes= s.getBytes(Charset.defaultCharset());
					        System.out.println(bytes.length);
					        ByteArrayOutputStream ba=new ByteArrayOutputStream();
					        String result=ba.toString(Charset.defaultCharset().displayName());
					        try (
					            InputStreamReader is=new InputStreamReader(new FileInputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
					           ){ } catch (FileNotFoundException e) {
					            e.printStackTrace();
					            }
					    }
					}
					""",
				false, null);

		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
